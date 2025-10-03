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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "subscriptions", schema = "public", indexes = {
        @Index(name = "idx_subscription_contractor", columnList = "contractor_id"),
        @Index(name = "idx_subscription_client", columnList = "client_id"),
        @Index(name = "idx_subscription_status", columnList = "status"),
        @Index(name = "idx_subscription_dates", columnList = "start_date, end_date"),
        @Index(name = "idx_subscription_next_billing", columnList = "next_billing_date")
})
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "contractor_id", nullable = false)
    private Contractor contractor;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @NotNull
    @Column(name = "value", nullable = false, precision = 18, scale = 2)
    private BigDecimal value;

    @Size(max = 20)
    @NotNull
    @Column(name = "frequency", nullable = false, length = 20)
    private String frequency;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @NotNull
    @Column(name = "billing_day", nullable = false)
    private Integer billingDay;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = false;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @Size(max = 500)
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @OneToMany(mappedBy = "subscription")
    private Set<Transaction> transactions = new LinkedHashSet<>();

}