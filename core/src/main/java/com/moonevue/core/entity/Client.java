package com.moonevue.core.entity;

import com.moonevue.core.enums.ClientStatus;
import com.moonevue.core.value.Address;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

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
    @OnDelete(action = OnDeleteAction.CASCADE)
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

    @Embedded
    private Address address = new Address();

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private ClientStatus status = ClientStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "client")
    private Set<Subscription> subscriptions = new LinkedHashSet<>();
}
