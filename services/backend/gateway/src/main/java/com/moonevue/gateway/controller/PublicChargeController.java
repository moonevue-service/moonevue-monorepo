package com.moonevue.gateway.controller;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Charge;
import com.moonevue.core.enums.Environment;
import com.moonevue.core.repository.BankConfigurationRepository;
import com.moonevue.core.repository.ChargeRepository;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.dto.PublicChargeRequest;
import com.moonevue.gateway.dto.PublicChargeResponse;
import com.moonevue.gateway.service.CheckoutClientUpsertService;
import com.moonevue.gateway.service.IdempotencyService;
import com.moonevue.gateway.service.PaymentService;
import com.moonevue.gateway.service.PublicChargeMapper;
import com.moonevue.gateway.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API pública de cobranças, autenticada por API Key. Reaproveita o {@link PaymentService}
 * usado pelo fluxo interno; a única diferença é a origem da autenticação (API Key) e a
 * autorização por escopos da chave.
 */
@RestController
@RequestMapping("/api/v1")
public class PublicChargeController {

    private static final Logger log = LoggerFactory.getLogger(PublicChargeController.class);

    private final PaymentService paymentService;
    private final PublicChargeMapper publicChargeMapper;
    private final IdempotencyService idempotencyService;
    private final RateLimiterService rateLimiterService;
    private final ChargeRepository chargeRepository;
    private final BankConfigurationRepository bankConfigurationRepository;
    private final CheckoutClientUpsertService checkoutClientUpsertService;

    public PublicChargeController(PaymentService paymentService,
                                  PublicChargeMapper publicChargeMapper,
                                  IdempotencyService idempotencyService,
                                  RateLimiterService rateLimiterService,
                                  ChargeRepository chargeRepository,
                                  BankConfigurationRepository bankConfigurationRepository,
                                  CheckoutClientUpsertService checkoutClientUpsertService) {
        this.paymentService = paymentService;
        this.publicChargeMapper = publicChargeMapper;
        this.idempotencyService = idempotencyService;
        this.rateLimiterService = rateLimiterService;
        this.chargeRepository = chargeRepository;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.checkoutClientUpsertService = checkoutClientUpsertService;
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping(Authentication authentication) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return error(HttpStatus.UNAUTHORIZED, "unauthorized", "API Key inválida");
        }
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "scopes", authentication.getAuthorities().stream().map(Object::toString).toList(),
                "status", "ok"
        ));
    }

    @PostMapping("/charges")
    public ResponseEntity<?> createCharge(Authentication authentication,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                          @RequestBody PublicChargeRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return error(HttpStatus.UNAUTHORIZED, "unauthorized", "API Key inválida");
        }
        if (!hasScope(authentication, "charges:write")) {
            return error(HttpStatus.FORBIDDEN, "forbidden", "A chave não possui o escopo charges:write");
        }

        ResponseEntity<?> envCheck = validateEnvironment(authentication, tenantId, request.bankConfigurationId());
        if (envCheck != null) {
            return envCheck;
        }

        Long apiKeyId = extractApiKeyId(authentication);
        ResponseEntity<?> limited = enforceRateLimit(apiKeyId);
        if (limited != null) {
            return limited;
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "idempotency_key_required",
                    "Header Idempotency-Key é obrigatório para criação de cobrança");
        }

        String scope = "public_charge_" + tenantId;
        String requestHash = idempotencyService.hashRequest(request);
        var existing = idempotencyService.findByScopeAndKey(scope, idempotencyKey);
        if (existing.isPresent() && existing.get().responseStatus() != null && existing.get().responseBody() != null) {
            if (!existing.get().requestHash().equals(requestHash)) {
                return error(HttpStatus.CONFLICT, "idempotency_conflict",
                        "Idempotency-Key reutilizada com payload diferente");
            }
            return ResponseEntity.status(existing.get().responseStatus())
                    .body(idempotencyService.parseResponseBody(existing.get().responseBody()));
        }
        idempotencyService.reserve(scope, idempotencyKey, requestHash);

        try {
            Long clientId = resolveClientId(tenantId, request);
            ChargeRequestDTO internal = publicChargeMapper.toChargeRequest(request);
            ChargeResponseDTO created = paymentService.createCharge(tenantId, internal, clientId, apiKeyId);
            PublicChargeResponse response = publicChargeMapper.toPublicResponse(request, created);
            idempotencyService.storeResponse(scope, idempotencyKey, HttpStatus.CREATED.value(), response);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_request", e.getMessage());
        } catch (Exception e) {
            log.error("[PublicChargeController] Falha ao criar cobrança via API: {}", e.getMessage(), e);
            return error(HttpStatus.BAD_GATEWAY, "provider_error", "Falha ao criar cobrança no provedor");
        }
    }

    @GetMapping("/charges/{chargeRef}")
    public ResponseEntity<?> getCharge(Authentication authentication,
                                       @PathVariable("chargeRef") String chargeRef) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return error(HttpStatus.UNAUTHORIZED, "unauthorized", "API Key inválida");
        }
        if (!hasScope(authentication, "charges:read")) {
            return error(HttpStatus.FORBIDDEN, "forbidden", "A chave não possui o escopo charges:read");
        }

        Charge charge = chargeRepository.findFirstByTenantIdAndProviderRef(tenantId, chargeRef).orElse(null);
        if (charge == null) {
            return error(HttpStatus.NOT_FOUND, "not_found", "Cobrança não encontrada");
        }
        return ResponseEntity.ok(publicChargeMapper.toPublicResponse(charge));
    }

    // ---- helpers ----

    private ResponseEntity<?> enforceRateLimit(Long apiKeyId) {
        if (apiKeyId == null) {
            return null;
        }
        RateLimiterService.Result result = rateLimiterService.tryAcquire("apikey:" + apiKeyId);
        if (!result.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(Math.max(1, result.resetEpochSeconds() - (System.currentTimeMillis() / 1000L))))
                    .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                    .header("X-RateLimit-Remaining", "0")
                    .body(Map.of("error", Map.of("code", "rate_limited", "message", "Limite de requisições excedido")));
        }
        return null;
    }

    private Long extractTenantId(Authentication authentication) {
        Object details = authentication != null ? authentication.getDetails() : null;
        if (details instanceof Map<?, ?> map && map.get("tenantId") instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private Long extractApiKeyId(Authentication authentication) {
        Object details = authentication != null ? authentication.getDetails() : null;
        if (details instanceof Map<?, ?> map && map.get("apiKeyId") instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private String extractEnvironment(Authentication authentication) {
        Object details = authentication != null ? authentication.getDetails() : null;
        if (details instanceof Map<?, ?> map && map.get("environment") instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Cria (ou reaproveita) o cliente local a partir dos dados de {@code customer} enviados na
     * cobrança via API, vinculando-o à transação — espelhando o fluxo interno em que um cliente
     * cadastrado é associado à geração de cobrança. Retorna {@code null} quando não há dados
     * suficientes (documento e nome são obrigatórios).
     */
    private Long resolveClientId(Long tenantId, PublicChargeRequest request) {
        PublicChargeRequest.Customer customer = request.customer();
        if (customer == null
                || customer.document() == null || customer.document().isBlank()
                || customer.name() == null || customer.name().isBlank()) {
            return null;
        }
        String bankProvider = request.bank() != null ? request.bank().name() : null;
        return checkoutClientUpsertService.upsertActiveClient(
                tenantId,
                customer.document(),
                customer.name(),
                customer.email(),
                customer.phone(),
                bankProvider);
    }

    /**
     * Garante que o ambiente da API Key seja compatível com o da BankConfiguration alvo:
     * chave {@code LIVE} só emite em configuração {@code PRODUCTION}; chave {@code TEST}
     * (homologação) só emite em {@code SANDBOX}. Evita cobrança real acidental com chave de teste.
     */
    private ResponseEntity<?> validateEnvironment(Authentication authentication, Long tenantId, Long bankConfigurationId) {
        String keyEnv = extractEnvironment(authentication);
        if (keyEnv == null || bankConfigurationId == null) {
            return null; // sem dados suficientes: validação downstream cuida do resto
        }
        BankConfiguration cfg = bankConfigurationRepository.findById(bankConfigurationId).orElse(null);
        if (cfg == null || cfg.getTenant() == null || !cfg.getTenant().getId().equals(tenantId)) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_request",
                    "BankConfiguration não encontrada para o tenant: " + bankConfigurationId);
        }
        Environment expected = "LIVE".equalsIgnoreCase(keyEnv) ? Environment.PRODUCTION : Environment.SANDBOX;
        if (cfg.getEnvironment() != expected) {
            String keyLabel = "LIVE".equalsIgnoreCase(keyEnv) ? "LIVE (produção)" : "TEST (homologação)";
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "environment_mismatch",
                    "A API Key " + keyLabel + " só pode emitir cobranças em configurações bancárias "
                            + expected + ". A configuração " + bankConfigurationId + " é " + cfg.getEnvironment() + ".");
        }
        return null;
    }

    private boolean hasScope(Authentication authentication, String scope) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> scope.equalsIgnoreCase(a.getAuthority()));
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", Map.of("code", code, "message", message)));
    }
}
