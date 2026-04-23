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
        var authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority(r.getName()))
                .toList();

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
}