package com.moonevue.auth.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogTest {

    private final PermissionCatalog permissionCatalog = new PermissionCatalog();

    @Test
    void permissionsForRoles_deveria_incluir_permissoes_administrativas_para_admin_tenant() {
        var permissions = permissionCatalog.permissionsForRoles(List.of("ADMIN_TENANT"));

        assertTrue(permissions.contains("employees.create"));
        assertTrue(permissions.contains("charges.emit"));
        assertTrue(permissions.contains("webhooks.reprocess"));
    }

    @Test
    void permissionsForRoles_deveria_limitar_support_a_permissoes_basicas() {
        var permissions = permissionCatalog.permissionsForRoles(List.of("SUPPORT"));

        assertTrue(permissions.contains("charges.read"));
        assertTrue(permissions.contains("audit.read"));
        assertFalse(permissions.contains("employees.create"));
        assertFalse(permissions.contains("roles.manage"));
    }
}