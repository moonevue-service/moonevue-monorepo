package com.moonevue.gateway.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.apache.hc.client5.http.utils.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class WebhookSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureFilter.class);

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

        byte[] body = StreamUtils.copyToByteArray(req.getInputStream());
        var wrapped = new CachedBodyHttpServletRequest(req, body);

        String sig = wrapped.getHeader(signatureHeader);
        if (!StringUtils.hasText(sig) || !StringUtils.hasText(hmacSecret)) {
            log.warn("Webhook assinatura ausente/segredo vazio uri={}", req.getRequestURI());
            res.sendError(401); return;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Hex.encodeHexString(mac.doFinal(body));
            String normalizedSig = sig.trim();
            if (normalizedSig.regionMatches(true, 0, "sha256=", 0, 7)) {
                normalizedSig = normalizedSig.substring(7);
            }

            // Fallback opcional: permite token compartilhado igual ao segredo em ambientes legados.
            if (hmacSecret.equals(normalizedSig)) {
                log.info("Webhook autenticado por token compartilhado uri={}", req.getRequestURI());
                setWebhookAuthentication();
                chain.doFilter(wrapped, res);
                return;
            }

            if (!expected.equalsIgnoreCase(normalizedSig)) {
                log.warn("Webhook assinatura inválida uri={}", req.getRequestURI());
                res.sendError(401); return;
            }
        } catch (Exception e) {
            log.error("Webhook erro na validação de assinatura uri={}: {}", req.getRequestURI(), e.getMessage());
            res.sendError(401); return;
        }

        log.info("Webhook autenticado por HMAC uri={}", req.getRequestURI());
        setWebhookAuthentication();
        chain.doFilter(wrapped, res);
    }

    private void setWebhookAuthentication() {
        var auth = new AbstractAuthenticationToken(List.of(new SimpleGrantedAuthority("WEBHOOK"))) {
            @Override public Object getCredentials() { return ""; }
            @Override public Object getPrincipal() { return "webhook"; }
            @Override public boolean isAuthenticated() { return true; }
        };
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // leitura síncrona; nada a fazer.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
