package com.moonevue.core.repository;

import com.moonevue.core.entity.Installment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // Conveniência por tenant (útil para telas/admin)
    Page<Installment> findByTenantId(Long tenantId, Pageable pageable);

    // Agregações
    // Nota: Para JPQL usar SUM precisa estar num @Query; aqui deixamos como estava no seu código original
    // Se precisar por tenant, crie query específica com tenantId no WHERE.
}