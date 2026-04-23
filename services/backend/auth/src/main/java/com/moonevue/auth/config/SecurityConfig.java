package com.moonevue.auth.config;

import com.moonevue.auth.security.InternalAuthFilter;
import com.moonevue.auth.security.LocalSessionAuthFilter;
import com.moonevue.auth.service.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Value("${moonevue.auth.internal-token}")
    private String internalToken;

    @Value("${moonevue.auth.cookie-name:sid}")
    private String cookieName;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionService sessionService) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(auth -> auth
                // Swagger e recursos públicos
                .requestMatchers("/", "/favicon.ico").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Auth público (POST)
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/logout").permitAll()

                // Sessao local ou acesso interno
                .requestMatchers("/auth/introspect", "/auth/touch").authenticated()

                .requestMatchers("/auth/employees/**").authenticated()

                .anyRequest().denyAll()
        );

        // Evita prompt de Basic Auth no navegador
        http.httpBasic(b -> b.disable());
        http.exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                .accessDeniedHandler((req, res, ex) -> res.sendError(403)));

        http.addFilterBefore(new LocalSessionAuthFilter(sessionService, cookieName),
                UsernamePasswordAuthenticationFilter.class);

        http.addFilterBefore(new InternalAuthFilter(internalToken), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
