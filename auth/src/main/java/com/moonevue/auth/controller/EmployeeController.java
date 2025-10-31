package com.moonevue.auth.controller;

import com.moonevue.auth.service.SessionService;
import com.moonevue.auth.service.UserService;
import com.moonevue.core.entity.User;
import com.moonevue.core.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final SessionService sessions;
    private final UserRepository users;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> createEmployee(@Valid @RequestBody EmployeeRegisterRequest body, HttpServletRequest req) {
        var current = getCurrentUser(req);
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Verifica se é admin do tenant (ajuste conforme seus papéis)
        boolean isAdmin = current.getRoles().stream().anyMatch(r -> "ROLE_TENANT_ADMIN".equals(r.getName()) || "ROLE_ADMIN".equals(r.getName()));
        if (!isAdmin) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));

        var tenant = current.getTenant();
        var created = userService.createUser(tenant, body.getEmail(), body.getPassword());
        // Opcional: atribuir papel específico ao funcionário (ex.: ROLE_EMPLOYEE)
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "userId", created.getId(),
                "email", created.getEmail(),
                "tenantId", tenant.getId()
        ));
    }

    private User getCurrentUser(HttpServletRequest req) {
        var c = WebUtils.getCookie(req, "sid"); // ou injete o nome do cookie via @Value, similar ao AuthController
        if (c == null) return null;
        Optional<UUID> sid;
        try { sid = Optional.of(UUID.fromString(c.getValue())); } catch (Exception e) { return null; }
        return sid.flatMap(sessions::findActive).map(s -> s.getUser()).orElse(null);
    }

    @Data
    public static class EmployeeRegisterRequest {
        @Email @NotBlank
        private String email;

        @NotBlank
        private String password;
    }
}