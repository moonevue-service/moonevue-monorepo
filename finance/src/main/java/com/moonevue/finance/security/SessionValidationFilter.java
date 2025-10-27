package com.moonevue.finance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class SessionValidationFilter extends OncePerRequestFilter {

    private final RestTemplate rest;
    private final String authBaseUrl;
    private final String internalToken;
    private final String cookieName;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // bypass para endpoints públicos do gateway (ajuste conforme necessário)
        var path = req.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs"))
        { chain.doFilter(req, res); return; }

        String sid = null;
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (cookieName.equals(c.getName())) { sid = c.getValue(); break; }
            }
        }
        if (!StringUtils.hasText(sid)) { res.setStatus(HttpStatus.UNAUTHORIZED.value()); return; }

        // Introspect
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.add(HttpHeaders.COOKIE, cookieName + "=" + sid);
        var entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp;
        try {
            resp = rest.exchange(authBaseUrl + "/auth/introspect", HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value()); return;
        }
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value()); return;
        }

        var body = resp.getBody();
        var email = String.valueOf(body.get("email"));
        var roles = (List<String>) body.get("roles");
        var auth = new AbstractAuthenticationToken(
                roles.stream().map(SimpleGrantedAuthority::new).toList()) {
            @Override public Object getCredentials() { return ""; }
            @Override public Object getPrincipal() { return email; }
            @Override public boolean isAuthenticated() { return true; }
        };
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Tenta renovar (touch). Se vier novo Set-Cookie, repassa ao cliente
        try {
            var touchResp = rest.exchange(authBaseUrl + "/auth/touch", HttpMethod.POST, entity, Void.class);
            var setCookie = touchResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            if (StringUtils.hasText(setCookie)) {
                res.setHeader(HttpHeaders.SET_COOKIE, setCookie);
            }
        } catch (Exception ignored) {}

        chain.doFilter(req, res);
    }
}
