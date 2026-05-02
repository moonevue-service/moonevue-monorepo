package com.moonevue.core.repository;

import com.moonevue.core.entity.BankAccount;
import com.moonevue.core.enums.BankType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    // Scoping por tenant
    Optional<BankAccount> findByIdAndTenantId(Long id, Long tenantId);

    // Unicidade da conta bancária por tenant
    boolean existsByTenantIdAndBankAndCdAgencyAndCdAccountAndCdAccountDigit(
            Long tenantId, BankType bank, String cdAgency, String cdAccount, String cdAccountDigit);

    Optional<BankAccount> findByTenantIdAndBankAndCdAgencyAndCdAccountAndCdAccountDigit(
            Long tenantId, BankType bank, String cdAgency, String cdAccount, String cdAccountDigit);

        List<BankAccount> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
}