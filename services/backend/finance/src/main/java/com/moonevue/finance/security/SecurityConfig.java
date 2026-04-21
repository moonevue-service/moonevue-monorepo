package com.moonevue.finance.security;

import com.moonevue.core.security.SessionValidationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${auth.base-url}")
    private String authBaseUrl;

    @Value("${auth.internal-token}")
    private String internalToken;

    @Value("${auth.cookie-name:sid}")
    private String cookieName;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder rtb) {
        return rtb.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RestTemplate restTemplate) throws Exception {

        http.csrf(csrf -> csrf.disable());
        http.httpBasic(b -> b.disable());

        http.authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
        );

        http.exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                .accessDeniedHandler((req, res, ex) -> res.sendError(403))
        );

        http.addFilterBefore(
                new SessionValidationFilter(restTemplate, authBaseUrl, internalToken, cookieName),
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}