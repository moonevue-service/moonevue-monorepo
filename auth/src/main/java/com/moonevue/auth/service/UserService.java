package com.moonevue.auth.service;

import com.moonevue.core.entity.Role;
import com.moonevue.core.entity.User;
import com.moonevue.core.repository.RoleRepository;
import com.moonevue.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    @Transactional
    public User createUser(String email, String rawPassword) {
        var normalized = email.trim().toLowerCase();
        if (users.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new DataIntegrityViolationException("email already exists");
        }

        var user = new User();
        user.setEmail(normalized);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setEnabled(true);

        // garante ROLE_USER
        var roleUser = roles.findByName("ROLE_USER")
                .orElseGet(() -> {
                    var r = new Role();
                    r.setName("ROLE_USER");
                    return roles.save(r);
                });

        user.setRoles(new HashSet<>());
        user.getRoles().add(roleUser);

        return users.save(user);
    }
}
