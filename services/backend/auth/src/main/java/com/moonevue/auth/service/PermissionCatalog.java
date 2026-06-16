package com.moonevue.auth.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PermissionCatalog {

    public Set<String> permissionsForRoles(List<String> roleNames) {
        Set<String> permissions = new LinkedHashSet<>();

        for (String roleName : roleNames) {
            if (roleName == null) {
                continue;
            }

            String normalized = roleName.toUpperCase(Locale.ROOT);
            switch (normalized) {
                case "ADMIN", "ADMIN_TENANT" -> permissions.addAll(List.of(
                        "customers.read",
                        "customers.create",
                        "customers.update",
                        "customers.merge",
                        "transactions.read",
                        "transactions.create",
                        "transactions.update",
                        "transactions.cancel",
                        "charges.read",
                        "charges.emit",
                        "charges.emit_immediate",
                        "charges.retry",
                        "employees.read",
                        "employees.create",
                        "employees.activate",
                        "employees.deactivate",
                        "roles.manage",
                        "audit.read",
                        "webhooks.reprocess"
                ));
                case "FINANCE" -> permissions.addAll(List.of(
                        "customers.read",
                        "customers.create",
                        "customers.update",
                        "transactions.read",
                        "transactions.create",
                        "transactions.update",
                        "charges.read",
                        "charges.emit",
                        "charges.retry",
                        "audit.read"
                ));
                case "SUPPORT", "EMPLOYED", "USER" -> permissions.addAll(List.of(
                        "customers.read",
                        "transactions.read",
                        "charges.read",
                        "audit.read"
                ));
                default -> {
                }
            }
        }

        return permissions;
    }
}