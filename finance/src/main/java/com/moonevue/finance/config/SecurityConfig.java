package com.moonevue.finance.config;

import com.moonevue.finance.security.SessionValidationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Value("${auth.base-url}") private String authBaseUrl;
    @Value("${auth.internal-token}") private String internalToken;
    @Value("${auth.cookie-name:sid}") private String cookieName;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestTemplateBuilder rtb) throws Exception {

        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
        );
        http.addFilterBefore(
                new SessionValidationFilter(rtb.build(), authBaseUrl, internalToken, cookieName),
                org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class
        );
        http.httpBasic(Customizer.withDefaults()); // opcional
        return http.build();
    }
}
