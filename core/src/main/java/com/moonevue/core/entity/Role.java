package com.moonevue.core.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "auth_role")
@Getter @Setter @NoArgsConstructor
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name; // ex: ROLE_USER, ROLE_ADMIN
}
