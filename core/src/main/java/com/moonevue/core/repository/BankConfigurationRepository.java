package com.moonevue.core.repository;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BankConfigurationRepository extends JpaRepository<BankConfiguration, Long> {

    Optional<BankConfiguration> findByTenantIdAndBankAccountIdAndEnvironment(
            Long tenantId, Long bankAccountId, Environment environment);

    List<BankConfiguration> findByTenantIdAndIsActiveTrue(Long tenantId);

    List<BankConfiguration> findByBankAccountIdAndIsActiveTrue(Long bankAccountId);

    Page<BankConfiguration> findByEnvironment(Environment environment, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BankConfiguration b set b.lastSyncAt = :when where b.id = :id")
    int touchLastSync(@Param("id") Long id, @Param("when") OffsetDateTime when);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BankConfiguration b set b.isActive = :active where b.id = :id")
    int setActive(@Param("id") Long id, @Param("active") boolean active);
}