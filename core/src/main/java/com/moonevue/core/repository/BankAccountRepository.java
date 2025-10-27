package com.moonevue.core.repository;

import com.moonevue.core.entity.BankAccount;
import com.moonevue.core.enums.BankType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByContractorId(Long contractorId);

    Page<BankAccount> findByContractorId(Long contractorId, Pageable pageable);

    Page<BankAccount> findByContractorIdAndActiveTrue(Long contractorId, Pageable pageable);

    boolean existsByContractorIdAndBankAndCdAgencyAndCdAccountAndCdAccountDigit(
            Long contractorId, BankType bank, String cdAgency, String cdAccount, String cdAccountDigit);

    Optional<BankAccount> findByContractorIdAndBankAndCdAgencyAndCdAccountAndCdAccountDigit(
            Long contractorId, BankType bank, String cdAgency, String cdAccount, String cdAccountDigit);
}
