package com.moonevue.auth.service;

import com.moonevue.core.entity.AuthRole;
import com.moonevue.core.entity.Tenant;
import com.moonevue.core.entity.User;
import com.moonevue.core.repository.RoleRepository;
import com.moonevue.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Transactional
    public User createUser(Tenant tenant, String email, String rawPassword, List<AuthRole> roles) {
        var normalized = email.trim().toLowerCase();
        if (users.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new DataIntegrityViolationException("email already exists");
        }

        var user = new User();
        user.setTenant(tenant);
        user.setEmail(normalized);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setEnabled(true);

        user.setRoles(new HashSet<>());
        user.getRoles().addAll(roles);

        return users.save(user);
    }
}