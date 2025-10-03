package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "transactions", schema = "public", indexes = {
        @Index(name = "idx_tx_account_id", columnList = "account_id"),
        @Index(name = "idx_tx_subscription", columnList = "subscription_id"),
        @Index(name = "idx_tx_status", columnList = "status"),
        @Index(name = "idx_tx_created_at", columnList = "created_at")
})
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Size(max = 20)
    @NotNull
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @Size(max = 200)
    @Column(name = "external_reference", length = 200)
    private String externalReference;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "transaction")
    private Set<Installment> installments = new LinkedHashSet<>();

    @OneToMany(mappedBy = "transaction")
    private Set<TransactionLog> transactionLogs = new LinkedHashSet<>();

}