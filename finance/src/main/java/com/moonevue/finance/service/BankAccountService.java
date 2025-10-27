package com.moonevue.finance.service;

import com.moonevue.finance.dto.bankaccount.BankAccountRequest;
import com.moonevue.finance.dto.bankaccount.BankAccountResponse;
import com.moonevue.finance.exception.ConflictException;
import com.moonevue.finance.exception.NotFoundException;
import com.moonevue.core.entity.BankAccount;
import com.moonevue.core.entity.Contractor;
import com.moonevue.core.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final ContractorService contractorService;

    @Transactional
    public BankAccountResponse create(Long contractorId, BankAccountRequest req) {
        Contractor contractor = contractorService.getEntity(contractorId);

        if (bankAccountRepository.existsByContractorIdAndBankAndCdAgencyAndCdAccountAndCdAccountDigit(
                contractorId, req.bank(), req.cdAgency(), req.cdAccount(), req.cdAccountDigit())) {
            throw new ConflictException("Bank account already exists for this contractor");
        }

        BankAccount b = new BankAccount();
        b.setContractor(contractor);
        b.setName(req.name());
        b.setCdAgency(req.cdAgency());
        b.setCdAccount(req.cdAccount());
        b.setCdAccountDigit(req.cdAccountDigit());
        b.setBank(req.bank());
        b.setAccountType(req.accountType());
        b.setActive(req.active() == null ? Boolean.TRUE : req.active());
        bankAccountRepository.save(b);
        return BankAccountResponse.from(b);
    }

    @Transactional(readOnly = true)
    public BankAccount getEntity(Long bankAccountId) {
        return bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new NotFoundException("Bank account not found: " + bankAccountId));
    }

    @Transactional
    public BankAccountResponse update(Long contractorId, Long bankAccountId, BankAccountRequest req) {
        BankAccount b = getEntity(bankAccountId);
        if (!b.getContractor().getId().equals(contractorId)) {
            throw new NotFoundException("Bank account does not belong to contractor");
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
    public void delete(Long contractorId, Long bankAccountId) {
        BankAccount b = getEntity(bankAccountId);
        if (!b.getContractor().getId().equals(contractorId)) {
            throw new NotFoundException("Bank account does not belong to contractor");
        }
        bankAccountRepository.delete(b);
    }
}
