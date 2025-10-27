package com.moonevue.gateway.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.utils.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class WebhookSignatureFilter extends OncePerRequestFilter {

    @Value("${moonevue.gateway.webhooks.hmac.secret}")
    private String hmacSecret;

    @Value("${moonevue.gateway.webhooks.hmac.header:X-Signature}")
    private String signatureHeader;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith("/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // wrap para ler o corpo e ainda permitir o controller consumir depois
        var wrapped = new ContentCachingRequestWrapper(req);

        String sig = wrapped.getHeader(signatureHeader);
        if (!StringUtils.hasText(sig) || !StringUtils.hasText(hmacSecret)) {
            res.sendError(401); return;
        }

        chain.doFilter(wrapped, res); // deixa o body ser preenchido no cache
        byte[] body = wrapped.getContentAsByteArray();

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Hex.encodeHexString(mac.doFinal(body));
            if (!expected.equalsIgnoreCase(sig)) { res.sendError(401); return; }
        } catch (Exception e) {
            res.sendError(401); return;
        }

        var auth = new AbstractAuthenticationToken(List.of(new SimpleGrantedAuthority("WEBHOOK"))) {
            @Override public Object getCredentials() { return ""; }
            @Override public Object getPrincipal() { return "webhook"; }
            @Override public boolean isAuthenticated() { return true; }
        };
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(req, res);
    }
}
