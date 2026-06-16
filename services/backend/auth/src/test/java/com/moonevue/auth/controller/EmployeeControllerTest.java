package com.moonevue.auth.controller;

import com.moonevue.auth.service.SessionService;
import com.moonevue.auth.service.PermissionCatalog;
import com.moonevue.auth.service.UserService;
import com.moonevue.core.entity.AuthRole;
import com.moonevue.core.entity.Session;
import com.moonevue.core.entity.Tenant;
import com.moonevue.core.entity.User;
import com.moonevue.core.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock
    private SessionService sessions;

    @Mock
    private UserService userService;

    @Mock
    private PermissionCatalog permissionCatalog;

    @Mock
    private RoleRepository roles;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EmployeeController controller = new EmployeeController(sessions, userService, roles, permissionCatalog);
        ReflectionTestUtils.setField(controller, "cookieName", "sid");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createEmployee_deveria_retornar_403_quando_usuario_sem_permissao() throws Exception {
        var request = Map.of("email", "func@teste.com", "password", "senha123");
        var current = buildUserWithRole("SUPPORT");
        when(sessions.findActive(any(UUID.class))).thenReturn(Optional.of(buildSession(current)));

        mockMvc.perform(post("/auth/employees")
                .cookie(new jakarta.servlet.http.Cookie("sid", UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEmployee_deveria_retornar_401_sem_cookie() throws Exception {
        var request = Map.of("email", "func@teste.com", "password", "senha123");
        mockMvc.perform(post("/auth/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    private User buildUserWithRole(String roleName) {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin@teste.com");
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        user.setTenant(tenant);
        AuthRole role = new AuthRole();
        role.setName(roleName);
        user.setRoles(new java.util.HashSet<>(List.of(role)));
        when(permissionCatalog.permissionsForRoles(List.of(roleName))).thenReturn(Set.of());
        return user;
    }

    private Session buildSession(User user) {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        return session;
    }
}
