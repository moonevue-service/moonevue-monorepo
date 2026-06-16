package com.moonevue.core.entity;

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

@Getter
@Setter
@Entity
@Table(name = "charges",
        indexes = {
                @Index(name = "idx_charges_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_charges_transaction", columnList = "transaction_id"),
                @Index(name = "idx_charges_provider_txid", columnList = "provider, provider_txid")
        }
)
public class Charge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "charge_id", nullable = false)
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

    @Size(max = 40)
    @NotNull
    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Size(max = 120)
    @Column(name = "provider_charge_id", length = 120)
    private String providerChargeId;

    @Size(max = 120)
    @Column(name = "provider_txid", length = 120)
    private String providerTxid;

    @Size(max = 20)
    @NotNull
    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Size(max = 30)
    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @NotNull
    @Column(name = "amount_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountTotal;

    @NotNull
    @Column(name = "amount_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "fee_amount", precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", precision = 18, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "pix_copy_paste", columnDefinition = "text")
    private String pixCopyPaste;

    @Column(name = "pix_qr_code_ref", columnDefinition = "text")
    private String pixQrCodeRef;

    @Size(max = 255)
    @Column(name = "boleto_line", length = 255)
    private String boletoLine;

    @Column(name = "boleto_pdf_ref", columnDefinition = "text")
    private String boletoPdfRef;

    @Size(max = 120)
    @Column(name = "card_authorization_ref", length = 120)
    private String cardAuthorizationRef;

    @Size(max = 30)
    @NotNull
    @Column(name = "reconciliation_state", nullable = false, length = 30)
    private String reconciliationState = "PENDING";

    @Size(max = 60)
    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Size(max = 1000)
    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_payload", columnDefinition = "jsonb")
    private String providerPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    private String providerResponse;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}