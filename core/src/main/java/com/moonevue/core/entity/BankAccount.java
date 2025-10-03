package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "bank_accounts", schema = "public", indexes = {
        @Index(name = "idx_account_owner", columnList = "owner")
}, uniqueConstraints = {
        @UniqueConstraint(name = "bank_accounts_number_key", columnNames = {"number"})
})
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 200)
    @NotNull
    @Column(name = "owner", nullable = false, length = 200)
    private String owner;

    @Size(max = 100)
    @NotNull
    @Column(name = "number", nullable = false, length = 100)
    private String number;

    @NotNull
    @Column(name = "balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Size(max = 100)
    @NotNull
    @Column(name = "bank", nullable = false, length = 100)
    private String bank;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "active", nullable = false)
    private Boolean active = false;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "bankAccount")
    private Set<BankConfiguration> bankConfigurations = new LinkedHashSet<>();

    @OneToMany(mappedBy = "account")
    private Set<Transaction> transactions = new LinkedHashSet<>();

}