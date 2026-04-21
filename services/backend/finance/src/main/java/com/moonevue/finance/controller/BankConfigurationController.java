package com.moonevue.finance.controller;

import com.moonevue.finance.dto.bankconfig.BankConfigurationRequest;
import com.moonevue.finance.dto.bankconfig.BankConfigurationResponse;
import com.moonevue.finance.dto.bankconfig.BankConfigurationUpdateRequest;
import com.moonevue.finance.dto.bankconfig.CertificateUploadResponse;
import com.moonevue.finance.service.BankConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration")
public class BankConfigurationController {

    private final BankConfigurationService bankConfigurationService;

    @PostMapping
    public ResponseEntity<BankConfigurationResponse> create(@PathVariable("tenantId") Long tenantId,
                                                            @PathVariable("bankAccountId") Long bankAccountId,
                                                            @Valid @RequestBody BankConfigurationRequest req,
                                                            UriComponentsBuilder uriBuilder) {
        var resp = bankConfigurationService.create(tenantId, bankAccountId, req);
        var uri = uriBuilder.path("/api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration/{id}")
                .buildAndExpand(tenantId, bankAccountId, resp.id()).toUri();
        return ResponseEntity.created(uri).body(resp);
    }

    @PutMapping("/{configId}")
    public ResponseEntity<BankConfigurationResponse> update(@PathVariable("tenantId") Long tenantId,
                                                            @PathVariable("bankAccountId") Long bankAccountId,
                                                            @PathVariable("configId") Long configId,
                                                            @Valid @RequestBody BankConfigurationUpdateRequest req) {
        return ResponseEntity.ok(bankConfigurationService.update(tenantId, bankAccountId, configId, req));
    }

    @PostMapping(path = "/{configId}/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CertificateUploadResponse> uploadCertificate(@PathVariable("tenantId") Long tenantId,
                                                                       @PathVariable("bankAccountId") Long bankAccountId,
                                                                       @PathVariable("configId") Long configId,
                                                                       @RequestPart("file") MultipartFile file,
                                                                       @RequestPart(value = "password", required = false) String password) throws IOException {
        var resp = bankConfigurationService.uploadCertificate(tenantId, bankAccountId, configId, file, password);
        return ResponseEntity.ok(resp);
    }
}
