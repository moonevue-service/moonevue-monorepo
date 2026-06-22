package com.moonevue.gateway.controller;

import com.moonevue.core.security.IntrospectedAuthToken;
import com.moonevue.gateway.dto.ApiKeyDTO;
import com.moonevue.gateway.dto.CreateApiKeyRequest;
import com.moonevue.gateway.dto.IntegrationAnalyticsDTO;
import com.moonevue.gateway.service.ApiKeyService;
import com.moonevue.gateway.service.IntegrationAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gestão de API Keys pelo usuário interno (autenticado por sessão).
 *
 * Não confundir com {@link PublicChargeController} (uso das chaves, sob /api/v1).
 * Aqui o ator é o administrador do tenant que cria/revoga credenciais.
 */
@RestController
@RequestMapping("/integrations/api-keys")
public class IntegrationController {

    private static final List<String> MANAGE_AUTHORITIES = List.of("integrations.manage", "ADMIN_TENANT", "ADMIN");

    private final ApiKeyService apiKeyService;
    private final IntegrationAnalyticsService analyticsService;

    public IntegrationController(ApiKeyService apiKeyService, IntegrationAnalyticsService analyticsService) {
        this.apiKeyService = apiKeyService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!canManage(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: integrations.manage"));
        }
        List<ApiKeyDTO> items = apiKeyService.list(tenantId).stream().map(ApiKeyDTO::from).toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<?> create(Authentication authentication, @RequestBody CreateApiKeyRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!canManage(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: integrations.manage"));
        }
        try {
            ApiKeyService.CreatedApiKey created = apiKeyService.create(
                    tenantId,
                    extractUserId(authentication),
                    request.name(),
                    request.environment(),
                    request.scopes(),
                    request.expiresAt()
            );
            ApiKeyDTO dto = ApiKeyDTO.from(created.apiKey(), created.plaintextKey());
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revoke(Authentication authentication, @PathVariable("id") Long id) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!canManage(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: integrations.manage"));
        }
        try {
            apiKeyService.revoke(tenantId, id, extractUserId(authentication));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/rotate")
    public ResponseEntity<?> rotate(Authentication authentication, @PathVariable("id") Long id) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!canManage(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: integrations.manage"));
        }
        try {
            ApiKeyService.CreatedApiKey created = apiKeyService.rotate(tenantId, id, extractUserId(authentication));
            ApiKeyDTO dto = ApiKeyDTO.from(created.apiKey(), created.plaintextKey());
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> analytics(Authentication authentication,
                                       @RequestParam(name = "days", defaultValue = "30") int days) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!canManage(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: integrations.manage"));
        }
        IntegrationAnalyticsDTO data = analyticsService.getAnalytics(tenantId, days);
        return ResponseEntity.ok(data);
    }

    private boolean canManage(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a != null)
                .anyMatch(a -> MANAGE_AUTHORITIES.stream().anyMatch(m -> m.equalsIgnoreCase(a)));
    }

    private Long extractTenantId(Authentication authentication) {
        return extractDetail(authentication, "tenantId");
    }

    private Long extractUserId(Authentication authentication) {
        return extractDetail(authentication, "userId");
    }

    private Long extractDetail(Authentication authentication, String key) {
        if (authentication instanceof IntrospectedAuthToken token
                && token.getDetails() instanceof Map<?, ?> map
                && map.get(key) instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
