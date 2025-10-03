package com.moonevue.gateway.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {
//    private final AccountRepository repo;
//
//    public AccountService(AccountRepository repo) { this.repo = repo; }
//
//    @Transactional(readOnly = true)
//    public List<BankAccount> findAll() { return repo.findAll(); }
//
//    @Transactional(readOnly = true)
//    public BankAccount findById(Long id) {
//        return repo.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
//    }
//
//    @Transactional
//    public BankAccount create(AccountDto dto) {
//        BankAccount a = BankAccount.builder()
//                .owner(dto.owner())
//                .number(dto.number())
//                .bank(dto.bank())
//                .active(Boolean.TRUE.equals(dto.active()))
//                .build();
//        return repo.save(a);
//    }
//
//    @Transactional
//    public BankAccount update(Long id, AccountDto dto) {
//        BankAccount a = findById(id);
//        a.setOwner(dto.owner());
//        a.setNumber(dto.number());
//        a.setBank(dto.bank());
//        a.setActive(Boolean.TRUE.equals(dto.active()));
//        return repo.save(a);
//    }
//
//    @Transactional
//    public void delete(Long id) {
//        repo.deleteById(id);
//    }
}