package com.moonevue.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Set;

@Entity @Table(name = "auth_user")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String passwordHash;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "auth_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;

}