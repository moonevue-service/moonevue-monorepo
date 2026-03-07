package com.moonevue.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class SessionValidationFilter extends OncePerRequestFilter {

    private final RestTemplate rest;
    private final String authBaseUrl;
    private final String internalToken;
    private final String cookieName;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/webhooks/", "/actuator/", "/swagger-ui", "/v3/api-docs"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = new UrlPathHelper().getPathWithinApplication(request);
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String sid = extractSessionCookie(req);
        if (!StringUtils.hasText(sid)) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        ResponseEntity<Map> resp = introspect(sid);
        if (resp == null || !resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(buildToken(resp.getBody()));
        tryTouch(buildHeaders(sid), res);

        chain.doFilter(req, res);
    }

    private String extractSessionCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        return Arrays.stream(req.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseEntity<Map> introspect(String sid) {
        try {
            return rest.exchange(authBaseUrl + "/auth/introspect",
                    HttpMethod.GET, new HttpEntity<>(buildHeaders(sid)), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private IntrospectedAuthToken buildToken(Map<?, ?> body) {
        var email = String.valueOf(body.get("email"));

        List<String> roles = body.get("roles") instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();

        Long tenantId = body.get("tenantId") instanceof Number n ? n.longValue() : null;
        Long userId = body.get("userId") instanceof Number n ? n.longValue() : null;

        var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
        return new IntrospectedAuthToken(email, authorities, tenantId, userId);
    }

    private HttpHeaders buildHeaders(String sid) {
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.add(HttpHeaders.COOKIE, cookieName + "=" + sid);
        return headers;
    }

    private void tryTouch(HttpHeaders headers, HttpServletResponse res) {
        try {
            var touchResp = rest.exchange(authBaseUrl + "/auth/touch",
                    HttpMethod.POST, new HttpEntity<>(headers), Void.class);
            var setCookie = touchResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            if (StringUtils.hasText(setCookie)) {
                res.setHeader(HttpHeaders.SET_COOKIE, setCookie);
            }
        } catch (Exception ignored) {
        }
    }
}
