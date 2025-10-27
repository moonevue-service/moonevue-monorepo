package com.moonevue.gateway.config;

import com.moonevue.gateway.security.SessionValidationFilter;
import com.moonevue.gateway.webhook.WebhookSignatureFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Value("${auth.base-url}") private String authBaseUrl;
    @Value("${auth.internal-token}") private String internalToken;
    @Value("${auth.cookie-name:sid}") private String cookieName;

    private final WebhookSignatureFilter webhookSignatureFilter;

    public SecurityConfig(WebhookSignatureFilter webhookSignatureFilter) {
        this.webhookSignatureFilter = webhookSignatureFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestTemplateBuilder rtb) throws Exception {
        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Webhooks: exigem autoridade "WEBHOOK" (atribuída pelo filtro de assinatura)
                .requestMatchers(HttpMethod.POST, "/webhooks/**").hasAuthority("WEBHOOK")
                .anyRequest().authenticated()
        );

        // 1) Validação de assinatura de webhook (atribui autoridade WEBHOOK)
        http.addFilterBefore(webhookSignatureFilter, AnonymousAuthenticationFilter.class);

        // 2) Validação de sessão via cookie para demais rotas (ignora /webhooks/** dentro do próprio filtro)
        http.addFilterBefore(
                new SessionValidationFilter(rtb.build(), authBaseUrl, internalToken, cookieName),
                AnonymousAuthenticationFilter.class
        );

        // Evita pop-up de Basic Auth
        http.httpBasic(b -> b.disable());

        return http.build();
    }
}
