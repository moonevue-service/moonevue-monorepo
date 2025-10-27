package com.moonevue.core.repository;

import com.moonevue.core.entity.Installment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findByTransactionIdOrderByInstallmentNumberAsc(Long transactionId);

    Optional<Installment> findByTransactionIdAndInstallmentNumber(Long transactionId, Integer installmentNumber);

    Page<Installment> findByStatus(String status, Pageable pageable);

    List<Installment> findByDueDateBeforeAndPaidDateIsNull(LocalDate date);

    @Query("""
           select coalesce(sum(i.amount), 0)
           from Installment i
           where i.transaction.id = :transactionId
           """)
    BigDecimal sumAmountByTransaction(Long transactionId);

    @Query("""
           select coalesce(sum(i.amount), 0)
           from Installment i
           where i.transaction.id = :transactionId
             and i.paidDate is null
           """)
    BigDecimal sumOpenAmountByTransaction(Long transactionId);
}
