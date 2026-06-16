package com.moonevue.core.entity;

import com.moonevue.core.enums.CheckoutAccessMode;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "transactions",
        indexes = {
                @Index(name = "idx_tx_tenant", columnList = "tenant_id"),
                @Index(name = "idx_tx_account_id", columnList = "account_id"),
                @Index(name = "idx_tx_subscription", columnList = "subscription_id"),
                @Index(name = "idx_tx_status", columnList = "status"),
                @Index(name = "idx_tx_created_at", columnList = "created_at")
        }
)
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount bankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_configuration_id")
    private BankConfiguration bankConfiguration;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "client_id")
        private Client client;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type = TransactionType.CHARGE;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @Size(max = 200)
    @Column(name = "external_reference", length = 200)
    private String externalReference;

    @Column(name = "checkout_token")
    private UUID checkoutToken;

    @Column(name = "checkout_expires_at")
    private OffsetDateTime checkoutExpiresAt;

    @Column(name = "checkout_instrument", length = 30)
    private String checkoutInstrument;

        @Enumerated(EnumType.STRING)
        @Column(name = "checkout_access_mode", length = 30)
        private CheckoutAccessMode checkoutAccessMode = CheckoutAccessMode.PUBLIC;

        @Column(name = "checkout_identity_verified_at")
        private OffsetDateTime checkoutIdentityVerifiedAt;

    @Column(name = "checkout_pix_key", length = 255)
    private String checkoutPixKey;

    @Column(name = "payer_name", length = 200)
    private String payerName;

    @Column(name = "payer_email", length = 200)
    private String payerEmail;

    @Column(name = "payer_document", length = 20)
    private String payerDocument;

    @Column(name = "payer_phone", length = 30)
    private String payerPhone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_payload", columnDefinition = "jsonb")
    private String providerPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    private String providerResponse;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Size(max = 1000)
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "fee_amount", precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", precision = 18, scale = 2)
    private BigDecimal netAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TransactionLog> transactionLogs = new LinkedHashSet<>();
}