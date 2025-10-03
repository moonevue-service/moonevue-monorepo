package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "clients", schema = "public", indexes = {
        @Index(name = "idx_client_contractor", columnList = "contractor_id"),
        @Index(name = "idx_client_cpf_cnpj", columnList = "cpf_cnpj"),
        @Index(name = "idx_client_email", columnList = "email"),
        @Index(name = "idx_client_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_contractor_client_cpf_cnpj", columnNames = {"contractor_id", "cpf_cnpj"})
})
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contractor_id", nullable = false)
    private Contractor contractor;

    @Size(max = 200)
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 14)
    @NotNull
    @Column(name = "cpf_cnpj", nullable = false, length = 14)
    private String cpfCnpj;

    @Size(max = 100)
    @NotNull
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Size(max = 20)
    @Column(name = "phone", length = 20)
    private String phone;

    @Size(max = 200)
    @Column(name = "address_street", length = 200)
    private String addressStreet;

    @Size(max = 20)
    @Column(name = "address_number", length = 20)
    private String addressNumber;

    @Size(max = 100)
    @Column(name = "address_complement", length = 100)
    private String addressComplement;

    @Size(max = 100)
    @Column(name = "address_neighborhood", length = 100)
    private String addressNeighborhood;

    @Size(max = 100)
    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Size(max = 2)
    @Column(name = "address_state", length = 2)
    private String addressState;

    @Size(max = 10)
    @Column(name = "address_zip_code", length = 10)
    private String addressZipCode;

    @Size(max = 50)
    @ColumnDefault("'Brasil'")
    @Column(name = "address_country", length = 50)
    private String addressCountry;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "client")
    private Set<Subscription> subscriptions = new LinkedHashSet<>();

}