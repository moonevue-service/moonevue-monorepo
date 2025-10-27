package com.moonevue.core.repository;

import com.moonevue.core.entity.TransactionLog;
import com.moonevue.core.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {

    Page<TransactionLog> findByTransactionIdOrderByEventDateDesc(Long transactionId, Pageable pageable);

    Page<TransactionLog> findBySeverity(Severity severity, Pageable pageable);

    Page<TransactionLog> findByEventType(String eventType, Pageable pageable);

    Page<TransactionLog> findByEventDateBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
