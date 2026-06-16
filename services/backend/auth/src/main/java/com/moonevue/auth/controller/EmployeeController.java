package com.moonevue.auth.controller;

import com.moonevue.auth.service.SessionService;
import com.moonevue.auth.service.PermissionCatalog;
import com.moonevue.auth.service.UserService;
import com.moonevue.core.entity.AuthRole;
import com.moonevue.core.entity.Session;
import com.moonevue.core.entity.User;
import com.moonevue.core.enums.RoleAuth;
import com.moonevue.core.repository.RoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import java.util.*;

@RestController
@RequestMapping("/auth/employees")
@RequiredArgsConstructor
public class EmployeeController {

    @Value("${moonevue.auth.cookie.name}")
    private String cookieName;

    private final SessionService sessions;
    private final UserService userService;
    private final RoleRepository roles;
    private final PermissionCatalog permissionCatalog;

    @PostMapping
    public ResponseEntity<?> createEmployee(@Valid @RequestBody EmployeeRegisterRequest body, HttpServletRequest req) {
        var current = getCurrentUser(req);
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (!hasAnyPermission(current, "employees.create", "employees.activate", "roles.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden: employees.create"));
        }

        var tenant = current.getTenant();
        Optional<AuthRole> role = roles.findById(RoleAuth.EMPLOYED.getId());
        if (role.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        var created = userService.createUser(tenant, body.getEmail(), body.getPassword(), List.of(role.get()));
        // Opcional: atribuir papel específico ao funcionário (ex.: ROLE_EMPLOYEE)
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "userId", created.getId(),
                "email", created.getEmail(),
                "tenantId", tenant.getId()
        ));
    }

    private User getCurrentUser(HttpServletRequest req) {
        var c = WebUtils.getCookie(req, cookieName);
        if (c == null) return null;
        Optional<UUID> sid;
        try { sid = Optional.of(UUID.fromString(c.getValue())); } catch (Exception e) { return null; }
        return sid.flatMap(sessions::findActive).map(Session::getUser).orElse(null);
    }

    private boolean hasAnyPermission(User current, String... allowedPermissions) {
        Set<String> permissions = permissionCatalog.permissionsForRoles(current.getRoles().stream().map(AuthRole::getName).toList());
        for (String allowed : allowedPermissions) {
            if (allowed != null && permissions.contains(allowed)) {
                return true;
            }
        }
        return false;
    }

    @Data
    public static class EmployeeRegisterRequest {
        @Email @NotBlank
        private String email;

        @NotBlank
        private String password;
    }
}