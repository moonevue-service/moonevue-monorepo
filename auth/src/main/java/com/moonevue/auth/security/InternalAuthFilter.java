package com.moonevue.auth.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

public class InternalAuthFilter implements Filter {
    private final String expected;

    public InternalAuthFilter(String expected) { this.expected = expected; }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var r = (HttpServletRequest) req;
        var token = r.getHeader("X-Internal-Token");
        if (token != null && token.equals(expected)) {
            var auth = new AbstractAuthenticationToken(List.of(new SimpleGrantedAuthority("INTERNAL"))) {
                @Override public Object getCredentials() { return ""; }
                @Override public Object getPrincipal() { return "internal"; }
                @Override public boolean isAuthenticated() { return true; }
            };
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }
}
