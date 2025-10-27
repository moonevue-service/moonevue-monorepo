package com.moonevue.core.entity;

import com.moonevue.core.enums.PersonType;
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
@Table(name = "contractors", schema = "public", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contractor_cpf_cnpj", columnNames = {"cpf_cnpj"})
})
public class Contractor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contractor_id", nullable = false)
    private Long id;

    @Size(max = 200)
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 200)
    @Column(name = "business_name", length = 200)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "person_type", nullable = false, length = 2)
    private PersonType personType;

    @Size(max = 14)
    @NotNull
    @Column(name = "cpf_cnpj", nullable = false, length = 14)
    private String cpfCnpj;

    @Size(max = 100)
    @NotNull
    @Column(name = "contact_email", nullable = false, length = 100)
    private String contactEmail;

    @Size(max = 20)
    @Column(name = "phone", length = 20)
    private String phone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "contractor")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<BankAccount> bankAccounts = new LinkedHashSet<>();

    @OneToMany(mappedBy = "contractor")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<BankConfiguration> bankConfigurations = new LinkedHashSet<>();

    @OneToMany(mappedBy = "contractor")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<Client> clients = new LinkedHashSet<>();

    @OneToMany(mappedBy = "contractor")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<Subscription> subscriptions = new LinkedHashSet<>();
}
