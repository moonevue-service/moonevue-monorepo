package com.moonevue.finance.controller;

import com.moonevue.core.security.IntrospectedAuthToken;
import com.moonevue.finance.dto.bankconfig.BankConfigurationRequest;
import com.moonevue.finance.dto.bankconfig.BankConfigurationUpdateRequest;
import com.moonevue.finance.service.BankConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration")
@PreAuthorize("hasAnyAuthority('ADMIN_TENANT', 'ADMIN')")
public class BankConfigurationController {

    private final BankConfigurationService bankConfigurationService;

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("tenantId") Long tenantId,
                                  @PathVariable("bankAccountId") Long bankAccountId,
                                  Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        return ResponseEntity.ok(bankConfigurationService.list(tenantId, bankAccountId));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable("tenantId") Long tenantId,
                                    @PathVariable("bankAccountId") Long bankAccountId,
                                    @Valid @RequestBody BankConfigurationRequest req,
                                    Authentication auth,
                                    UriComponentsBuilder uriBuilder) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        var resp = bankConfigurationService.create(tenantId, bankAccountId, req);
        var uri = uriBuilder.path("/api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration/{id}")
                .buildAndExpand(tenantId, bankAccountId, resp.id()).toUri();
        return ResponseEntity.created(uri).body(resp);
    }

    @PutMapping("/{configId}")
    public ResponseEntity<?> update(@PathVariable("tenantId") Long tenantId,
                                    @PathVariable("bankAccountId") Long bankAccountId,
                                    @PathVariable("configId") Long configId,
                                    @Valid @RequestBody BankConfigurationUpdateRequest req,
                                    Authentication auth) {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        return ResponseEntity.ok(bankConfigurationService.update(tenantId, bankAccountId, configId, req));
    }

    @PostMapping(path = "/{configId}/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCertificate(@PathVariable("tenantId") Long tenantId,
                                               @PathVariable("bankAccountId") Long bankAccountId,
                                               @PathVariable("configId") Long configId,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestPart(value = "password", required = false) String password,
                                               Authentication auth) throws IOException {
        if (!isAuthorizedForTenant(auth, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acesso negado ao tenant"));
        }
        var resp = bankConfigurationService.uploadCertificate(tenantId, bankAccountId, configId, file, password);
        return ResponseEntity.ok(resp);
    }

    private boolean isAuthorizedForTenant(Authentication auth, Long tenantId) {
        if (!(auth instanceof IntrospectedAuthToken token)) return false;
        Object details = token.getDetails();
        if (!(details instanceof java.util.Map<?, ?> map)) return false;
        Object tid = map.get("tenantId");
        return tid instanceof Number n && n.longValue() == tenantId;
    }
}
