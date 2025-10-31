package com.moonevue.core.entity;

import com.moonevue.core.enums.AccountType;
import com.moonevue.core.enums.BankType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "bank_accounts",
        indexes = {
                @Index(name = "idx_account_tenant", columnList = "tenant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenant_account_unique",
                        columnNames = {"tenant_id", "bank", "cd_agency", "cd_account", "cd_account_digit"})
        })
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Size(max = 200)
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 100)
    @NotNull
    @Column(name = "cd_agency", nullable = false, length = 100)
    private String cdAgency;

    @Size(max = 100)
    @NotNull
    @Column(name = "cd_account", nullable = false, length = 100)
    private String cdAccount;

    @Size(max = 10)
    @Column(name = "cd_account_digit", length = 10)
    private String cdAccountDigit;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "bank", nullable = false, length = 100)
    private BankType bank;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20)
    private AccountType accountType = AccountType.CHECKING;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "bankAccount")
    private Set<BankConfiguration> bankConfigurations = new LinkedHashSet<>();

    @OneToMany(mappedBy = "bankAccount")
    private Set<Transaction> transactions = new LinkedHashSet<>();

    @OneToMany(mappedBy = "bankAccount")
    private Set<Subscription> subscriptions = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}