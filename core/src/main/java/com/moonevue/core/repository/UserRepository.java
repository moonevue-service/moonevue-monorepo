package com.moonevue.core.repository;

import com.moonevue.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Optional;

@EnableJpaRepositories
public interface UserRepository extends JpaRepository<com.moonevue.core.entity.User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
}
