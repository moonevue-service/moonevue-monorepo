package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Ligação entre um {@link Client} e um provedor de pagamento (EFI, ASAAS, ...).
 *
 * <p>Um mesmo cliente pode existir em vários bancos ao mesmo tempo, cada um com o
 * seu próprio identificador. Por isso o identificador bancário vive aqui e não na
 * tabela {@code clients}. Para provedores sem customer id externo real (ex.: EFI),
 * {@link #bankCustomerId} guarda um identificador interno sintético, mantendo a
 * mesma arquitetura para todos os bancos.
 */
@Getter
@Setter
@Entity
@Table(name = "client_bank_integrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_bank_provider", columnNames = {"client_id", "bank_provider"})
})
public class ClientBankIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    /**
     * Provedor de pagamento (ex.: {@code EFI}, {@code ASAAS}). Armazenado como texto
     * para permitir novos bancos sem alteração de schema/enum.
     */
    @NotNull
    @Size(max = 40)
    @Column(name = "bank_provider", nullable = false, length = 40)
    private String bankProvider;

    /**
     * Identificador do cliente no provedor. Pode ser nulo para provedores sem
     * customer id externo; nesse caso usa-se um identificador interno sintético.
     */
    @Size(max = 255)
    @Column(name = "bank_customer_id", length = 255)
    private String bankCustomerId;

    /** Detalhes específicos do provedor, sem poluir o core. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
