package com.moonevue.core.repository;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankConfigurationRepository extends JpaRepository<BankConfiguration, Long> {

    Optional<BankConfiguration> findByContractorIdAndBankAccountIdAndEnvironment(
            Long contractorId, Long bankAccountId, Environment environment);

    List<BankConfiguration> findByContractorIdAndIsActiveTrue(Long contractorId);

    List<BankConfiguration> findByBankAccountIdAndIsActiveTrue(Long bankAccountId);

    Page<BankConfiguration> findByEnvironment(Environment environment, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BankConfiguration b set b.lastSyncAt = :when where b.id = :id")
    int touchLastSync(Long id, OffsetDateTime when);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BankConfiguration b set b.isActive = :active where b.id = :id")
    int setActive(Long id, boolean active);
}
