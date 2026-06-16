package com.moonevue.gateway.controller;

import com.moonevue.core.security.IntrospectedAuthToken;
import com.moonevue.gateway.dto.ClientSummaryDTO;
import com.moonevue.gateway.dto.ClientUpsertRequest;
import com.moonevue.gateway.dto.TransactionSummaryDTO;
import com.moonevue.gateway.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "50") int size) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "customers.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: customers.read"));
        }
        Page<ClientSummaryDTO> result = clientService.list(tenantId, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable("clientId") Long clientId) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "customers.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: customers.read"));
        }
        try {
            return ResponseEntity.ok(clientService.get(tenantId, clientId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(Authentication authentication,
                                    @Valid @RequestBody ClientUpsertRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "customers.create")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: customers.create"));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(tenantId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{clientId}")
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable("clientId") Long clientId,
                                    @Valid @RequestBody ClientUpsertRequest request) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "customers.update")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: customers.update"));
        }
        try {
            return ResponseEntity.ok(clientService.update(tenantId, clientId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{clientId}/transactions")
    public ResponseEntity<?> listTransactions(Authentication authentication,
                                              @PathVariable("clientId") Long clientId,
                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                              @RequestParam(name = "size", defaultValue = "50") int size) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        if (!hasAnyAuthority(authentication, "customers.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: customers.read"));
        }
        Page<TransactionSummaryDTO> result = clientService.listTransactions(tenantId, clientId, page, size);
        return ResponseEntity.ok(result);
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
}
