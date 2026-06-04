package com.moonevue.finance.controller;

import com.moonevue.core.security.IntrospectedAuthToken;
import com.moonevue.finance.service.BankConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.moonevue.finance.config.TestSecurityConfig;

/**
 * Testes de autorização do BankConfigurationController.
 *
 * Regra: apenas ADMIN_TENANT ou ADMIN podem acessar configurações bancárias.
 * Outros papéis recebem 403; sem sessão recebem 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class BankConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BankConfigurationService bankConfigurationService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private IntrospectedAuthToken adminTenantToken(long tenantId) {
        return new IntrospectedAuthToken("admin@moonevue.test",
                List.of(new SimpleGrantedAuthority("ADMIN_TENANT")), tenantId, 1L);
    }

    private IntrospectedAuthToken userToken(long tenantId) {
        return new IntrospectedAuthToken("user@moonevue.test",
                List.of(new SimpleGrantedAuthority("USER")), tenantId, 2L);
    }

    private IntrospectedAuthToken financeToken(long tenantId) {
        return new IntrospectedAuthToken("finance@moonevue.test",
                List.of(new SimpleGrantedAuthority("FINANCE")), tenantId, 3L);
    }

    // ── testes ────────────────────────────────────────────────────────────────

    @Test
    void list_deveria_retornar_200_quando_admin_tenant() throws Exception {
        when(bankConfigurationService.list(42L, 7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/tenant/42/bank-account/7/configuration")
                        .with(authentication(adminTenantToken(42L))))
                .andExpect(status().isOk());
    }

    @Test
    void list_deveria_retornar_403_quando_papel_user() throws Exception {
        mockMvc.perform(get("/api/tenant/42/bank-account/7/configuration")
                        .with(authentication(userToken(42L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_deveria_retornar_403_quando_papel_finance() throws Exception {
        mockMvc.perform(get("/api/tenant/42/bank-account/7/configuration")
                        .with(authentication(financeToken(42L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_deveria_retornar_401_sem_autenticacao() throws Exception {
        mockMvc.perform(get("/api/tenant/42/bank-account/7/configuration"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_deveria_retornar_401_sem_cookie_no_header() throws Exception {
        mockMvc.perform(get("/api/tenant/42/bank-account/7/configuration")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}
