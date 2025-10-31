package com.moonevue.core.repository;

import com.moonevue.core.entity.Subscription;
import com.moonevue.core.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Page<Subscription> findByTenantId(Long tenantId, Pageable pageable);

    Page<Subscription> findByClientId(Long clientId, Pageable pageable);

    List<Subscription> findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus status, LocalDate until);

    List<Subscription> findByTenantIdAndStatus(Long tenantId, SubscriptionStatus status);

    Page<Subscription> findByBankAccountId(Long bankAccountId, Pageable pageable);
}