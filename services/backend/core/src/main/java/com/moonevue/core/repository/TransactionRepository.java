package com.moonevue.core.repository;

import com.moonevue.core.entity.Transaction;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
           select t from Transaction t
           join fetch t.bankAccount ba
           where t.tenant.id = :tenantId
           order by t.createdAt desc
           """)
    Page<Transaction> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    // Agregações corrigindo o path property para bankAccount
    @Query("""
           select coalesce(sum(t.amount), 0)
           from Transaction t
           where t.bankAccount.id = :accountId
             and t.status = :status
             and t.createdAt between :from and :to
           """)
    BigDecimal sumAmountByAccountAndStatusAndPeriod(@Param("accountId") Long accountId,
                                                    @Param("status") TransactionStatus status,
                                                    @Param("from") OffsetDateTime from,
                                                    @Param("to") OffsetDateTime to);

    @Query("""
           select coalesce(sum(t.netAmount), 0)
           from Transaction t
           where t.bankAccount.id = :accountId
             and t.status in :statuses
           """)
    BigDecimal sumNetAmountByAccountAndStatuses(@Param("accountId") Long accountId,
                                                @Param("statuses") List<TransactionStatus> statuses);
}