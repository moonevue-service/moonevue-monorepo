package com.moonevue.auth.controller;

import com.moonevue.auth.dto.RegisterRequest;
import com.moonevue.auth.service.SessionService;
import com.moonevue.auth.service.UserService;
import com.moonevue.core.entity.Session;
import com.moonevue.core.entity.Tenant;
import com.moonevue.core.repository.TenantRepository;
import com.moonevue.core.repository.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final UserService userService;

    @org.springframework.beans.factory.annotation.Value("${moonevue.auth.cookie.name}")
    private String cookieName;
    @org.springframework.beans.factory.annotation.Value("${moonevue.auth.cookie.domain}")
    private String cookieDomain;
    @org.springframework.beans.factory.annotation.Value("${moonevue.auth.cookie.path}")
    private String cookiePath;
    @org.springframework.beans.factory.annotation.Value("${moonevue.auth.cookie.secure}")
    private boolean cookieSecure;
    @org.springframework.beans.factory.annotation.Value("${moonevue.auth.cookie.same-site}")
    private String cookieSameSite;
    @org.springframework.beans.factory.annotation.Value("${moonevue.auth.cookie.max-age-seconds}")
    private long cookieMaxAge;

    // Registro: cria Tenant + primeiro usuário (owner/admin)
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest req) {
        if (!body.getPassword().equals(body.getConfirmPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "password_mismatch"));
        }

        if (tenants.findByDocument(body.getTenantDocument()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "tenant_document_already_exists"));
        }

        try {
            Tenant t = new Tenant();
            t.setName(body.getTenantName());
            t.setDocument(body.getTenantDocument());
            t.setActive(true);
            t = tenants.save(t);

            var user = userService.createUser(t, body.getEmail(), body.getPassword());

            String ua = resolveUserAgent(req);
            String ip = resolveClientIp(req);
            Session s = sessions.create(user, ip, ua);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.SET_COOKIE, buildCookie(s.getId().toString(), cookieMaxAge))
                    .body(Map.of(
                            "tenantId", t.getId(),
                            "userId", user.getId(),
                            "email", user.getEmail()
                    ));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "email_already_exists"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest req) {
        var user = users.findByEmailIgnoreCase(body.getEmail())
                .filter(u -> u.isEnabled() && encoder.matches(body.getPassword(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String ip = resolveClientIp(req);
        String ua = resolveUserAgent(req);
        String sid = readCookie(req, cookieName);

        if (sid != null) {
            var sOpt = tryParseUUID(sid).flatMap(sessions::findActive);
            if (sOpt.isPresent()) {
                var s = sOpt.get();
                if (s.getUser().getId().equals(user.getId())) {
                    if (sessions.shouldRenew(s)) {
                        s = sessions.touch(s);
                    }
                    return ResponseEntity.noContent()
                            .header(HttpHeaders.SET_COOKIE, buildCookie(s.getId().toString(), cookieMaxAge))
                            .build();
                } else {
                    sessions.revoke(s);
                }
            }
        }

        var existing = sessions.findActiveByUser(user);
        if (existing.isPresent()) {
            var s = existing.get();
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, buildCookie(s.getId().toString(), cookieMaxAge))
                    .build();
        }

        var s = sessions.create(user, ip, ua);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildCookie(s.getId().toString(), cookieMaxAge))
                .build();
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req) {
        String sid = readCookie(req, cookieName);
        if (sid != null) {
            tryParseUUID(sid).flatMap(sessions::findActive).ifPresent(sessions::revoke);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildCookie("", 0))
                .build();
    }

    @Hidden
    @GetMapping("/introspect")
    public ResponseEntity<?> introspect(HttpServletRequest req) {
        String sid = readCookie(req, cookieName);
        if (sid == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var opt = tryParseUUID(sid).flatMap(sessions::findActive);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var s = opt.get();
        var u = s.getUser();
        var t = u.getTenant();
        return ResponseEntity.ok(Map.of(
                "userId", u.getId(),
                "email", u.getEmail(),
                "tenantId", t != null ? t.getId() : null,
                "roles", u.getRoles().stream().map(r -> r.getName()).toList()
        ));
    }

    @Hidden
    @PostMapping("/touch")
    public ResponseEntity<?> touch(HttpServletRequest req) {
        String sid = readCookie(req, cookieName);
        if (sid == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var opt = tryParseUUID(sid).flatMap(sessions::findActive);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var s = opt.get();
        if (sessions.shouldRenew(s)) {
            s = sessions.touch(s);
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, buildCookie(s.getId().toString(), cookieMaxAge))
                    .build();
        }
        return ResponseEntity.noContent().build();
    }

    // Helpers

    private Optional<UUID> tryParseUUID(String val) {
        try {
            return Optional.of(UUID.fromString(val));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String readCookie(HttpServletRequest req, String name) {
        var c = WebUtils.getCookie(req, name);
        return c != null ? c.getValue() : null;
    }

    private String resolveUserAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return ua != null ? ua : "unknown";
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return req.getRemoteAddr();
    }

    private String buildCookie(String value, long maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .domain(cookieDomain)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(maxAge)
                .build().toString();
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }
}