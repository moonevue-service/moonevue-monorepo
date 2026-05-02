package com.moonevue.finance.service;

import com.moonevue.core.entity.BankAccount;
import com.moonevue.core.entity.Tenant;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.repository.BankAccountRepository;
import com.moonevue.finance.dto.bankaccount.BankAccountRequest;
import com.moonevue.finance.dto.bankaccount.BankAccountResponse;
import com.moonevue.finance.exception.ConflictException;
import com.moonevue.finance.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccounts;

    @Transactional
    public BankAccountResponse create(Long tenantId, BankAccountRequest req) {
        if (existsAccount(tenantId, req.bank(), req.cdAgency(), req.cdAccount(), req.cdAccountDigit())) {
            throw new ConflictException("Bank account already exists for this tenant");
        }

        var b = new BankAccount();
        var t = new Tenant(); t.setId(tenantId);
        b.setTenant(t);
        b.setName(req.name());
        b.setCdAgency(req.cdAgency());
        b.setCdAccount(req.cdAccount());
        b.setCdAccountDigit(req.cdAccountDigit());
        b.setBank(req.bank());
        b.setAccountType(req.accountType());
        b.setActive(req.active() == null ? Boolean.TRUE : req.active());

        bankAccounts.save(b);
        return BankAccountResponse.from(b);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> listByTenant(Long tenantId) {
        return bankAccounts.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(BankAccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BankAccount getEntity(Long tenantId, Long bankAccountId) {
        return bankAccounts.findByIdAndTenantId(bankAccountId, tenantId)
                .orElseThrow(() -> new NotFoundException("Bank account not found: " + bankAccountId));
    }

    @Transactional
    public BankAccountResponse update(Long tenantId, Long bankAccountId, BankAccountRequest req) {
        var b = getEntity(tenantId, bankAccountId);

        // Se chave primária (banco+agência+conta+digito) mudou, valida unicidade
        if (hasKeyChange(b, req) &&
                existsAccount(tenantId, req.bank(), req.cdAgency(), req.cdAccount(), req.cdAccountDigit())) {
            throw new ConflictException("Another bank account with same details exists for this tenant");
        }

        b.setName(req.name());
        b.setCdAgency(req.cdAgency());
        b.setCdAccount(req.cdAccount());
        b.setCdAccountDigit(req.cdAccountDigit());
        b.setBank(req.bank());
        if (req.accountType() != null) b.setAccountType(req.accountType());
        if (req.active() != null) b.setActive(req.active());

        return BankAccountResponse.from(b);
    }

    @Transactional
    public void delete(Long tenantId, Long bankAccountId) {
        var b = getEntity(tenantId, bankAccountId);
        bankAccounts.delete(b);
    }

    private boolean existsAccount(Long tenantId, BankType bank, String agency, String account, String digit) {
        return bankAccounts.existsByTenantIdAndBankAndCdAgencyAndCdAccountAndCdAccountDigit(
                tenantId, bank, agency, account, digit);
    }

    private boolean hasKeyChange(BankAccount b, BankAccountRequest req) {
        return !(safeEq(b.getBank(), req.bank())
                && safeEq(b.getCdAgency(), req.cdAgency())
                && safeEq(b.getCdAccount(), req.cdAccount())
                && safeEq(b.getCdAccountDigit(), req.cdAccountDigit()));
    }

    private static boolean safeEq(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}