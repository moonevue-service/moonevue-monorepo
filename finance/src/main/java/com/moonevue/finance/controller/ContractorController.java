package com.moonevue.finance.controller;

import com.moonevue.finance.dto.contractor.ContractorRequest;
import com.moonevue.finance.dto.contractor.ContractorResponse;
import com.moonevue.finance.service.ContractorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contractors")
public class ContractorController {

    private final ContractorService contractorService;

    @PostMapping
    public ResponseEntity<ContractorResponse> create(@Valid @RequestBody ContractorRequest req,
                                                     UriComponentsBuilder uriBuilder) {
        var resp = contractorService.create(req);
        var uri = uriBuilder.path("/api/contractors/{id}").buildAndExpand(resp.id()).toUri();
        return ResponseEntity.created(uri).body(resp);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractorResponse> update(@PathVariable("id") Long id,
                                                     @Valid @RequestBody ContractorRequest req) {
        return ResponseEntity.ok(contractorService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        contractorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
