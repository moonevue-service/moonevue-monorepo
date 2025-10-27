package com.moonevue.core.repository;

import com.moonevue.core.entity.Subscription;
import com.moonevue.core.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Page<Subscription> findByContractorId(Long contractorId, Pageable pageable);

    Page<Subscription> findByClientId(Long clientId, Pageable pageable);

    List<Subscription> findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus status, LocalDate until);

    List<Subscription> findByContractorIdAndStatus(Long contractorId, SubscriptionStatus status);
}
