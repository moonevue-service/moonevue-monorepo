package com.moonevue.gateway.controller;

import com.moonevue.gateway.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
//    private final AccountService service;
//
//    public AccountController(AccountService service) {
//        this.service = service;
//    }
//
//    @GetMapping
//    public List<BankAccount> list() { return service.findAll(); }
//
//    @GetMapping("/{id}")
//    public BankAccount get(@PathVariable Long id) { return service.findById(id); }
//
//    @PostMapping
//    public ResponseEntity<BankAccount> create(@Valid @RequestBody AccountDto dto) {
//        BankAccount saved = service.create(dto);
//        return ResponseEntity.created(URI.create("/api/accounts/" + saved.getId())).body(saved);
//    }
//
//    @PutMapping("/{id}")
//    public BankAccount update(@PathVariable Long id, @Valid @RequestBody AccountDto dto) {
//        return service.update(id, dto);
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> delete(@PathVariable Long id) {
//        service.delete(id);
//        return ResponseEntity.noContent().build();
//    }
}
