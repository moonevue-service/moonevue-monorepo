package com.moonevue.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class InternalAuthFilter extends OncePerRequestFilter {

    private final String expected;

    public InternalAuthFilter(String expected) {
        this.expected = expected;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/auth/introspect") && !path.equals("/auth/touch");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            chain.doFilter(req, res);
            return;
        }

        var token = req.getHeader("X-Internal-Token");
        if (token == null || !token.equals(expected)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var auth = new AbstractAuthenticationToken(List.of(new SimpleGrantedAuthority("INTERNAL"))) {
            @Override public Object getCredentials() { return ""; }
            @Override public Object getPrincipal() { return "internal"; }
            @Override public boolean isAuthenticated() { return true; }
        };
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(req, res);
    }
}