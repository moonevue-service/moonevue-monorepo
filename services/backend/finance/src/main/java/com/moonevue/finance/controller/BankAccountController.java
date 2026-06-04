package com.moonevue.finance.controller;

import com.moonevue.core.security.IntrospectedAuthToken;
import com.moonevue.finance.dto.bankaccount.BankAccountRequest;
import com.moonevue.finance.dto.bankaccount.BankAccountResponse;
import com.moonevue.finance.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/{tenantId}/bank-account")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("tenantId") Long tenantId, Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        return ResponseEntity.ok(bankAccountService.listByTenant(tenantId));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable("tenantId") Long tenantId,
                                                      @Valid @RequestBody BankAccountRequest req,
                                                      Authentication auth,
                                                      UriComponentsBuilder uriBuilder) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        var resp = bankAccountService.create(tenantId, req);
        var uri = uriBuilder.path("/api/tenant/{tenantId}/bank-account/{id}")
                .buildAndExpand(tenantId, resp.id()).toUri();
        return ResponseEntity.created(uri).body(resp);
    }

    @PutMapping("/{bankAccountId}")
    public ResponseEntity<?> update(@PathVariable("tenantId") Long tenantId,
                                                      @PathVariable("bankAccountId") Long bankAccountId,
                                                      @Valid @RequestBody BankAccountRequest req,
                                                      Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        return ResponseEntity.ok(bankAccountService.update(tenantId, bankAccountId, req));
    }

    @DeleteMapping("/{bankAccountId}")
    public ResponseEntity<?> delete(@PathVariable("tenantId") Long tenantId,
                                       @PathVariable("bankAccountId") Long bankAccountId,
                                       Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        bankAccountService.delete(tenantId, bankAccountId);
        return ResponseEntity.noContent().build();
    }

    private boolean isAuthorizedForTenant(Authentication auth, Long tenantId) {
        if (!(auth instanceof IntrospectedAuthToken token)) return false;
        Object details = token.getDetails();
        if (!(details instanceof java.util.Map<?, ?> map)) return false;
        Object tid = map.get("tenantId");
        return tid instanceof Number n && n.longValue() == tenantId;
    }
}
