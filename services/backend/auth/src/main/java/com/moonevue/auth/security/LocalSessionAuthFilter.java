package com.moonevue.auth.security;

import com.moonevue.auth.service.SessionService;
import com.moonevue.core.entity.Session;
import com.moonevue.core.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.UUID;

@RequiredArgsConstructor
public class LocalSessionAuthFilter extends OncePerRequestFilter {

    private final SessionService sessions;
    private final String cookieName;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/auth/login")
                || path.equals("/auth/register")
            || path.equals("/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        var cookie = WebUtils.getCookie(req, cookieName);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        User user = resolveUser(cookie.getValue());
        if (user == null) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(buildToken(user));
        chain.doFilter(req, res);
    }

    private User resolveUser(String cookieValue) {
        try {
            UUID sid = UUID.fromString(cookieValue);
            return sessions.findActive(sid).map(Session::getUser).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AbstractAuthenticationToken buildToken(User user) {
        var authorities = new LinkedHashSet<SimpleGrantedAuthority>();
        user.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority(r.getName()))
            .forEach(authorities::add);
        user.getRoles().stream()
            .map(r -> resolvePermissionsForRole(r.getName()))
            .flatMap(java.util.Collection::stream)
            .map(SimpleGrantedAuthority::new)
            .forEach(authorities::add);

        var auth = new AbstractAuthenticationToken(authorities) {
            @Override public Object getCredentials() { return ""; }
            @Override public Object getPrincipal() { return user.getEmail(); }
            @Override public boolean isAuthenticated() { return true; }
        };

        var details = new HashMap<String, Object>();
        details.put("tenantId", user.getTenant() != null ? user.getTenant().getId() : null);
        details.put("userId", user.getId());
        auth.setDetails(details);

        return auth;
    }

    private java.util.List<String> resolvePermissionsForRole(String roleName) {
        if (roleName == null) {
            return java.util.List.of();
        }

        String normalized = roleName.toUpperCase();
        return switch (normalized) {
            case "ADMIN", "ADMIN_TENANT" -> java.util.List.of(
                    "customers.read",
                    "customers.create",
                    "customers.update",
                    "customers.merge",
                    "transactions.read",
                    "transactions.create",
                    "transactions.update",
                    "transactions.cancel",
                    "charges.read",
                    "charges.emit",
                    "charges.emit_immediate",
                    "charges.retry",
                    "employees.read",
                    "employees.create",
                    "employees.activate",
                    "employees.deactivate",
                    "roles.manage",
                    "audit.read",
                    "webhooks.reprocess"
            );
            case "FINANCE" -> java.util.List.of(
                    "customers.read",
                    "customers.create",
                    "customers.update",
                    "transactions.read",
                    "transactions.create",
                    "transactions.update",
                    "charges.read",
                    "charges.emit",
                    "charges.retry",
                    "audit.read"
            );
            case "SUPPORT", "EMPLOYED", "USER" -> java.util.List.of(
                    "customers.read",
                    "transactions.read",
                    "charges.read",
                    "audit.read"
            );
            default -> java.util.List.of();
        };
    }
}