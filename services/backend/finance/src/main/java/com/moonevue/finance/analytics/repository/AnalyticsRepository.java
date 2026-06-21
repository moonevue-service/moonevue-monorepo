package com.moonevue.finance.analytics.repository;

import com.moonevue.core.entity.Transaction;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import com.moonevue.finance.analytics.repository.projection.ClientRevenueProjection;
import com.moonevue.finance.analytics.repository.projection.ReceivablesProjection;
import com.moonevue.finance.analytics.repository.projection.RevenueSummaryProjection;
import com.moonevue.finance.analytics.repository.projection.StatusBreakdownProjection;
import com.moonevue.finance.analytics.repository.projection.TimeSeriesProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repositório de leitura analítica sobre os dados do Finance.
 *
 * <p>Todas as agregações são executadas no banco (push-down) e sempre filtradas por
 * {@code tenant_id}. Queries JPQL são validadas no startup; a série temporal usa SQL
 * nativo por causa do {@code date_trunc} do Postgres.</p>
 */
public interface AnalyticsRepository extends Repository<Transaction, Long> {

    /** Agregados monetários do período (receita bruta/líquida, tarifas, estornos). */
    @Query("""
            select
                coalesce(sum(case when t.type = :chargeType and t.status in :paidStatuses then t.amount else 0 end), 0) as grossRevenue,
                coalesce(sum(case when t.type = :chargeType and t.status in :paidStatuses then t.netAmount else 0 end), 0) as netRevenue,
                coalesce(sum(case when t.status in :paidStatuses then t.feeAmount else 0 end), 0) as totalFees,
                coalesce(sum(case when t.type = :refundType and t.status in :paidStatuses then t.amount else 0 end), 0) as totalRefunds
            from Transaction t
            where t.tenant.id = :tenantId
              and t.createdAt between :from and :to
            """)
    RevenueSummaryProjection aggregateRevenue(@Param("tenantId") Long tenantId,
                                              @Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to,
                                              @Param("chargeType") TransactionType chargeType,
                                              @Param("refundType") TransactionType refundType,
                                              @Param("paidStatuses") Collection<TransactionStatus> paidStatuses);

    /** Número de cobranças pagas no período. */
    @Query("""
            select count(t)
            from Transaction t
            where t.tenant.id = :tenantId
              and t.type = :chargeType
              and t.status in :paidStatuses
              and t.createdAt between :from and :to
            """)
    long countPaidCharges(@Param("tenantId") Long tenantId,
                          @Param("from") OffsetDateTime from,
                          @Param("to") OffsetDateTime to,
                          @Param("chargeType") TransactionType chargeType,
                          @Param("paidStatuses") Collection<TransactionStatus> paidStatuses);

    /** Total de transações no período (para taxa de conversão). */
    @Query("""
            select count(t)
            from Transaction t
            where t.tenant.id = :tenantId
              and t.createdAt between :from and :to
            """)
    long countTotal(@Param("tenantId") Long tenantId,
                    @Param("from") OffsetDateTime from,
                    @Param("to") OffsetDateTime to);

    /** Clientes distintos que pagaram no período. */
    @Query("""
            select count(distinct t.client.id)
            from Transaction t
            where t.tenant.id = :tenantId
              and t.status in :paidStatuses
              and t.client is not null
              and t.createdAt between :from and :to
            """)
    long countPayingClients(@Param("tenantId") Long tenantId,
                            @Param("from") OffsetDateTime from,
                            @Param("to") OffsetDateTime to,
                            @Param("paidStatuses") Collection<TransactionStatus> paidStatuses);

    /** Ranking de clientes por receita (use {@link Pageable} para limitar o top-N). */
    @Query("""
            select t.client.id as clientId,
                   t.client.name as clientName,
                   coalesce(sum(t.amount), 0) as revenue,
                   count(t) as txCount
            from Transaction t
            where t.tenant.id = :tenantId
              and t.type = :chargeType
              and t.status in :paidStatuses
              and t.client is not null
              and t.createdAt between :from and :to
            group by t.client.id, t.client.name
            order by sum(t.amount) desc
            """)
    List<ClientRevenueProjection> rankClientsByRevenue(@Param("tenantId") Long tenantId,
                                                       @Param("from") OffsetDateTime from,
                                                       @Param("to") OffsetDateTime to,
                                                       @Param("chargeType") TransactionType chargeType,
                                                       @Param("paidStatuses") Collection<TransactionStatus> paidStatuses,
                                                       Pageable pageable);

    /** Distribuição de transações por status. */
    @Query("""
            select t.status as status,
                   count(t) as txCount,
                   coalesce(sum(t.amount), 0) as totalAmount
            from Transaction t
            where t.tenant.id = :tenantId
              and t.createdAt between :from and :to
            group by t.status
            order by sum(t.amount) desc
            """)
    List<StatusBreakdownProjection> breakdownByStatus(@Param("tenantId") Long tenantId,
                                                      @Param("from") OffsetDateTime from,
                                                      @Param("to") OffsetDateTime to);

    /** Recebíveis: total em aberto, vencido (overdue) e a vencer. */
    @Query("""
            select
                coalesce(sum(case when t.status in :receivableStatuses then t.amount else 0 end), 0) as totalReceivable,
                coalesce(sum(case when t.status in :receivableStatuses and t.dueDate is not null and t.dueDate < :today then t.amount else 0 end), 0) as totalOverdue,
                coalesce(sum(case when t.status in :receivableStatuses and (t.dueDate is null or t.dueDate >= :today) then t.amount else 0 end), 0) as totalToDue
            from Transaction t
            where t.tenant.id = :tenantId
            """)
    ReceivablesProjection aggregateReceivables(@Param("tenantId") Long tenantId,
                                               @Param("today") LocalDate today,
                                               @Param("receivableStatuses") Collection<TransactionStatus> receivableStatuses);

    /**
     * Série temporal de receita agregada por {@code date_trunc(:granularity, created_at)}.
     * Native query: {@code paidStatuses} é uma lista de nomes de status (varchar).
     */
    @Query(value = """
            select date_trunc(:granularity, t.created_at) as bucket,
                   coalesce(sum(case when t.type = 'CHARGE' and t.status in (:paidStatuses) then t.amount else 0 end), 0) as grossRevenue,
                   coalesce(sum(case when t.type = 'CHARGE' and t.status in (:paidStatuses) then t.net_amount else 0 end), 0) as netRevenue,
                   coalesce(sum(case when t.type = 'CHARGE' and t.status in (:paidStatuses) then 1 else 0 end), 0) as paidCount
            from transactions t
            where t.tenant_id = :tenantId
              and t.created_at between :from and :to
            group by 1
            order by 1
            """, nativeQuery = true)
    List<TimeSeriesProjection> revenueTimeSeries(@Param("tenantId") Long tenantId,
                                                 @Param("from") OffsetDateTime from,
                                                 @Param("to") OffsetDateTime to,
                                                 @Param("granularity") String granularity,
                                                 @Param("paidStatuses") Collection<String> paidStatuses);
}
