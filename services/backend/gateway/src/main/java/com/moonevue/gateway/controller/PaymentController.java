package com.moonevue.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.security.IntrospectedAuthToken;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.dto.ChargeSummaryDTO;
import com.moonevue.gateway.dto.CreateCheckoutTransactionRequest;
import com.moonevue.gateway.dto.TransactionSummaryDTO;
import com.moonevue.gateway.service.IdempotencyService;
import com.moonevue.gateway.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> listTransactions(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        Page<TransactionSummaryDTO> result = paymentService.listTransactions(tenantId, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createPayment(Authentication authentication,
                                           @Valid @RequestBody ChargeRequestDTO request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        return processCharge(tenantId, request);
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(Authentication authentication,
                                            @Valid @RequestBody CreateCheckoutTransactionRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        try {
            TransactionSummaryDTO response = paymentService.createCheckoutDraft(tenantId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/pix/immediate")
    public ResponseEntity<?> createPixImmediate(Authentication authentication,
                                                @Valid @RequestBody PixImmediateRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        ChargeRequestDTO charge = new ChargeRequestDTO(
            request.bank(),
            request.bankConfigurationId(),
            new ChargeRequestDTO.Payment(
                ChargeRequestDTO.Instrument.PIX_IMMEDIATE,
                request.payment(),
                null,
                null
            )
        );
        return processCharge(tenantId, charge, request.clientId());
    }

    @PostMapping("/pix/due")
    public ResponseEntity<?> createPixDue(Authentication authentication,
                                          @Valid @RequestBody PixDueRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        ChargeRequestDTO charge = new ChargeRequestDTO(
            request.bank(),
            request.bankConfigurationId(),
            new ChargeRequestDTO.Payment(
                ChargeRequestDTO.Instrument.PIX_DUE,
                null,
                request.payment(),
                null
            )
        );
        return processCharge(tenantId, charge, request.clientId());
    }

    @PostMapping("/boleto")
    public ResponseEntity<?> createBoleto(Authentication authentication,
                                          @Valid @RequestBody BoletoRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        ChargeRequestDTO charge = new ChargeRequestDTO(
            request.bank(),
            request.bankConfigurationId(),
            new ChargeRequestDTO.Payment(
                ChargeRequestDTO.Instrument.BOLETO,
                null,
                null,
                request.payment()
            )
        );
        return processCharge(tenantId, charge, request.clientId());
    }

    @PostMapping("/v1/transactions/{transactionId}/charges/emit")
    public ResponseEntity<?> emitChargeForTransaction(Authentication authentication,
                                                      @PathVariable("transactionId") Long transactionId,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                      @RequestBody(required = false) EmitChargeRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "charges.emit", "charges.emit_immediate")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: charges.emit"));
        }

        String scope = "emit_charge_tx_" + transactionId;
        String requestHash = idempotencyService.hashRequest(request == null ? Map.of() : request);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyService.findByScopeAndKey(scope, idempotencyKey);
            if (existing.isPresent() && existing.get().responseStatus() != null && existing.get().responseBody() != null) {
                if (!existing.get().requestHash().equals(requestHash)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Idempotency-Key reutilizada com payload diferente"));
                }
                return ResponseEntity.status(existing.get().responseStatus())
                        .body(idempotencyService.parseResponseBody(existing.get().responseBody()));
            }
            idempotencyService.reserve(scope, idempotencyKey, requestHash);
        }

        try {
            ChargeResponseDTO response = paymentService.emitChargeForTransaction(
                    tenantId,
                    transactionId,
                    request != null ? request.instrument() : null,
                    request != null ? request.pixKey() : null
            );

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.storeResponse(scope, idempotencyKey, HttpStatus.OK.value(), response);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/transactions/{transactionId}/charges/retry")
    public ResponseEntity<?> retryChargeForTransaction(Authentication authentication,
                                                       @PathVariable("transactionId") Long transactionId,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       @RequestBody(required = false) RetryChargeRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "charges.retry")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: charges.retry"));
        }

        String scope = "retry_charge_tx_" + transactionId;
        String requestHash = idempotencyService.hashRequest(request == null ? Map.of() : request);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyService.findByScopeAndKey(scope, idempotencyKey);
            if (existing.isPresent() && existing.get().responseStatus() != null && existing.get().responseBody() != null) {
                if (!existing.get().requestHash().equals(requestHash)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Idempotency-Key reutilizada com payload diferente"));
                }
                ChargeResponseDTO cached = idempotencyService.parseResponseBody(existing.get().responseBody(), ChargeResponseDTO.class);
                return ResponseEntity.status(existing.get().responseStatus()).body(cached);
            }
            idempotencyService.reserve(scope, idempotencyKey, requestHash);
        }

        try {
            ChargeResponseDTO response = paymentService.retryLastChargeForTransaction(
                    tenantId,
                    transactionId,
                    request != null ? request.pixKey() : null
            );

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.storeResponse(scope, idempotencyKey, HttpStatus.OK.value(), response);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/transactions/{transactionId}/charges")
    public ResponseEntity<?> listChargesByTransaction(Authentication authentication,
                                                      @PathVariable("transactionId") Long transactionId) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "charges.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: charges.read"));
        }

        try {
            List<ChargeSummaryDTO> items = paymentService.listChargesForTransaction(tenantId, transactionId);
            return ResponseEntity.ok(Map.of("items", items));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private Long extractTenantId(Authentication authentication) {
        if (authentication instanceof IntrospectedAuthToken token) {
            Object details = token.getDetails();
            if (details instanceof java.util.Map<?, ?> map) {
                Object tid = map.get("tenantId");
                if (tid instanceof Number n) return n.longValue();
            }
        }
        return null;
    }

    private ResponseEntity<?> processCharge(Long tenantId, ChargeRequestDTO request) {
        return processCharge(tenantId, request, null);
    }

    private ResponseEntity<?> processCharge(Long tenantId, ChargeRequestDTO request, Long clientId) {
        try {
            ChargeResponseDTO response = paymentService.createCharge(tenantId, request, clientId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("[PaymentController] Requisição inválida: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("[PaymentController] Resposta inválida do provedor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Resposta inválida do provedor", "detail", e.getMessage()));
        } catch (Exception e) {
            if (isEfiValidationError(e)) {
                return buildEfiValidationErrorResponse(e);
            }
            log.error("[PaymentController] Falha ao criar pagamento: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Falha ao criar pagamento", "detail", e.getMessage()));
        }
    }

    private boolean isEfiValidationError(Throwable error) {
        String message = rootMessage(error);
        return message != null
                && message.contains("HTTP 400")
                && message.contains("\"nome\":\"json_invalido\"");
    }

    private ResponseEntity<?> buildEfiValidationErrorResponse(Throwable error) {
        String message = rootMessage(error);
        String body = extractProviderBody(message);

        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "Dados inválidos para o provedor EFI",
                            "detail", "O payload enviado não atende ao schema exigido pela EFI"
                    ));
        }

        try {
            JsonNode json = objectMapper.readTree(body);
            java.util.ArrayList<String> details = new java.util.ArrayList<>();

            String providerMessage = json.path("mensagem").asText(null);
            if (providerMessage != null && !providerMessage.isBlank()) {
                details.add(providerMessage);
            }

            JsonNode erros = json.path("erros");
            if (erros.isArray()) {
                for (JsonNode item : erros) {
                    String caminho = item.path("caminho").asText("");
                    String msg = item.path("mensagem").asText("");
                    if (!msg.isBlank()) {
                        details.add(caminho.isBlank() ? msg : caminho + ": " + msg);
                    }
                }
            }

            String detail = details.isEmpty()
                    ? "O payload enviado não atende ao schema exigido pela EFI"
                    : String.join(" | ", details);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "Dados inválidos para o provedor EFI",
                            "provider", "EFI",
                            "providerError", json.path("nome").asText("json_invalido"),
                            "detail", detail,
                            "details", details
                    ));
        } catch (Exception parseEx) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "Dados inválidos para o provedor EFI",
                            "provider", "EFI",
                            "detail", body
                    ));
        }
    }

    private String extractProviderBody(String message) {
        if (message == null) return null;
        int idx = message.indexOf("HTTP 400 - ");
        if (idx < 0) return null;

        String body = message.substring(idx + "HTTP 400 - ".length()).trim();
        int lineBreak = body.indexOf('\n');
        if (lineBreak >= 0) {
            body = body.substring(0, lineBreak).trim();
        }
        return body.startsWith("{") ? body : null;
    }

    private String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : error.getMessage();
    }

    private boolean hasAnyAuthority(Authentication authentication, String... allowedAuthorities) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (var authority : authentication.getAuthorities()) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            for (String allowed : allowedAuthorities) {
                if (allowed.equalsIgnoreCase(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    public record PixImmediateRequest(
        BankType bank,
        Long bankConfigurationId,
        Long clientId,
        ChargeRequestDTO.PixImmediate payment
    ) {}

    public record PixDueRequest(
        BankType bank,
        Long bankConfigurationId,
        Long clientId,
        ChargeRequestDTO.PixDue payment
    ) {}

    public record BoletoRequest(
        BankType bank,
        Long bankConfigurationId,
        Long clientId,
        ChargeRequestDTO.Boleto payment
    ) {}

    public record EmitChargeRequest(
            String instrument,
            String pixKey
    ) {}

        public record RetryChargeRequest(
            String pixKey
        ) {}
}
