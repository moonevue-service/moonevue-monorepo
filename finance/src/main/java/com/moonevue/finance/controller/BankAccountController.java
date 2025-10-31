package com.moonevue.finance.controller;

import com.moonevue.finance.dto.bankaccount.BankAccountRequest;
import com.moonevue.finance.dto.bankaccount.BankAccountResponse;
import com.moonevue.finance.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/{contractorId}/bank-account")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @PostMapping
    public ResponseEntity<BankAccountResponse> create(@PathVariable("contractorId") Long contractorId,
                                                      @Valid @RequestBody BankAccountRequest req,
                                                      UriComponentsBuilder uriBuilder) {
        var resp = bankAccountService.create(contractorId, req);
        var uri = uriBuilder.path("/api/tenant/{contractorId}/bank-account/{id}")
                .buildAndExpand(contractorId, resp.id()).toUri();
        return ResponseEntity.created(uri).body(resp);
    }

    @PutMapping("/{bankAccountId}")
    public ResponseEntity<BankAccountResponse> update(@PathVariable("contractorId") Long contractorId,
                                                      @PathVariable("bankAccountId") Long bankAccountId,
                                                      @Valid @RequestBody BankAccountRequest req) {
        return ResponseEntity.ok(bankAccountService.update(contractorId, bankAccountId, req));
    }

    @DeleteMapping("/{bankAccountId}")
    public ResponseEntity<Void> delete(@PathVariable("contractorId") Long contractorId,
                                       @PathVariable("bankAccountId") Long bankAccountId) {
        bankAccountService.delete(contractorId, bankAccountId);
        return ResponseEntity.noContent().build();
    }
}
