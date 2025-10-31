package com.moonevue.core.entity;

import com.moonevue.core.enums.PaymentMethod;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "installments",
        indexes = {
                @Index(name = "idx_installment_tenant", columnList = "tenant_id"),
                @Index(name = "idx_installment_number", columnList = "transaction_id, installment_number"),
                @Index(name = "idx_installment_transaction", columnList = "transaction_id"),
                @Index(name = "idx_installment_due_date", columnList = "due_date"),
                @Index(name = "idx_installment_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tx_installment_unique", columnNames = {"transaction_id", "installment_number"})
        }
)
public class Installment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "installment_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @NotNull
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @NotNull
    @Column(name = "total_installments", nullable = false)
    private Integer totalInstallments;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "interest_amount", precision = 18, scale = 2)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "paid_amount", precision = 18, scale = 2)
    private BigDecimal paidAmount;

    @Size(max = 20)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PaymentMethod paymentMethod;

    @Size(max = 200)
    @Column(name = "payment_reference", length = 200)
    private String paymentReference;

    @Column(name = "overdue_days")
    private Integer overdueDays;

    @Column(name = "overdue_fee", precision = 18, scale = 2)
    private BigDecimal overdueFee = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}