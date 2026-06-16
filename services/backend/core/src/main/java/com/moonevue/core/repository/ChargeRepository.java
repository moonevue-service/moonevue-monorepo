package com.moonevue.core.repository;

import com.moonevue.core.entity.Charge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChargeRepository extends JpaRepository<Charge, Long> {
        @Query("""
                        select c from Charge c
                        where c.transaction.id = :transactionId
                            and c.tenant.id = :tenantId
                        order by c.createdAt desc
                        """)
        List<Charge> findByTransactionIdAndTenantIdOrderByCreatedAtDesc(@Param("transactionId") Long transactionId,
                                                                                                                                         @Param("tenantId") Long tenantId);

        Optional<Charge> findFirstByTransactionIdAndTenantIdOrderByCreatedAtDesc(Long transactionId,
                                                                                                                                                            Long tenantId);
}
