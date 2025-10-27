package com.moonevue.auth.config;

import com.moonevue.auth.security.InternalAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Value("${moonevue.auth.internal-token}")
    private String internalToken;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(auth -> auth
                // Swagger e recursos públicos
                .requestMatchers("/", "/favicon.ico").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Auth público (POST)
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/logout").permitAll()

                // Endpoints internos (somente gateway com header)
                .requestMatchers("/auth/introspect", "/auth/touch").hasAuthority("INTERNAL")

                .anyRequest().denyAll()
        );

        // Evita prompt de Basic Auth no navegador
        http.httpBasic(b -> b.disable());
        http.exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)));

        // Um AuthenticationProvider simples que injeta "INTERNAL" quando header confere (via filtro)
        http.addFilterBefore(new InternalAuthFilter(internalToken), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
