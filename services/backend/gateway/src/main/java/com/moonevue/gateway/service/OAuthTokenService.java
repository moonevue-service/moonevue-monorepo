package com.moonevue.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;
import com.moonevue.gateway.auth.AccessToken;
import com.moonevue.gateway.auth.OAuthClientCredentials;
import com.moonevue.gateway.http.RequestSender;
import com.moonevue.gateway.http.RequestSenderFactory;
import org.apache.hc.core5.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache por (bankType | configId | tokenUrl).
 */
@Service
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    /** Tentativas extras para recuperar cold start do stack mTLS/TLS (handshake/DNS lento na 1ª chamada). */
    private static final int TOKEN_FETCH_MAX_ATTEMPTS = 3;
    private static final long TOKEN_FETCH_BASE_BACKOFF_MS = 600;

    private final RequestSenderFactory senderFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, AccessToken> cache = new ConcurrentHashMap<>();

    public OAuthTokenService(RequestSenderFactory senderFactory, ObjectMapper objectMapper) {
        this.senderFactory = senderFactory;
        this.objectMapper = objectMapper;
    }

    public AccessToken getTokenFor(BankType bankType, String tokenUrl, OAuthClientCredentials creds, BankConfiguration cfg) {
        return getTokenFor(bankType, tokenUrl, creds, cfg, null);
    }

    /**
     * @param forceMtls true para forçar mTLS no token, false para forçar sender padrão, null para auto-detecção.
     */
    public AccessToken getTokenFor(BankType bankType,
                                   String tokenUrl,
                                   OAuthClientCredentials creds,
                                   BankConfiguration cfg,
                                   Boolean forceMtls) {
        String cacheKey = bankType.name() + "|" + cfg.getId() + "|" + tokenUrl;
        AccessToken cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        AccessToken fresh = fetchToken(bankType, tokenUrl, creds, cfg, forceMtls);
        cache.put(cacheKey, fresh);
        return fresh;
    }

    private AccessToken fetchToken(BankType bankType,
                                   String tokenUrl,
                                   OAuthClientCredentials creds,
                                   BankConfiguration cfg,
                                   Boolean forceMtls) {
        try {
            String basic = Base64.getEncoder()
                    .encodeToString((creds.getClientId() + ":" + creds.getClientSecret()).getBytes(StandardCharsets.UTF_8));

            Map<String, String> headers = Map.of(
                    "Content-Type", "application/json",
                    "Authorization", "Basic " + basic
            );

            String body = creds.getScope() != null && !creds.getScope().isBlank()
                    ? "{\"grant_type\":\"client_credentials\",\"scope\":\"" + escape(creds.getScope()) + "\"}"
                    : "{\"grant_type\":\"client_credentials\"}";

                RequestSender sender = resolveSender(bankType, cfg, tokenUrl, forceMtls);

                if (log.isDebugEnabled()) {
                log.debug("OAuth token request. bankType={} cfgId={} tokenUrl={} forceMtls={}",
                    bankType,
                    cfg != null ? cfg.getId() : null,
                    tokenUrl,
                    forceMtls);
                }

            String resp = sendWithRetry(sender, tokenUrl, body, headers, cfg);

            JsonNode json = objectMapper.readTree(resp);
            String accessToken = json.path("access_token").asText(null);
            String tokenType = json.path("token_type").asText("Bearer");
            int expiresIn = json.path("expires_in").asInt(3600);
            if (accessToken == null) {
                throw new IllegalStateException("Token response sem access_token: " + resp);
            }
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            return new AccessToken(accessToken, tokenType, expiresAt);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao obter token OAuth: " + e.getMessage(), e);
        }
    }

    /**
     * Executa a obtenção de token com retry em falhas transitórias de rede/TLS.
     * Seguro porque a troca de credenciais por token é idempotente e sem efeito colateral,
     * protegendo contra o "Read timed out" da primeira chamada (cold start do handshake mTLS).
     */
    private String sendWithRetry(RequestSender sender,
                                 String tokenUrl,
                                 String body,
                                 Map<String, String> headers,
                                 BankConfiguration cfg) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= TOKEN_FETCH_MAX_ATTEMPTS; attempt++) {
            try {
                return sender.send(Method.POST, tokenUrl, body, headers, cfg);
            } catch (Exception e) {
                last = e;
                if (attempt >= TOKEN_FETCH_MAX_ATTEMPTS || !isTransient(e)) {
                    throw e;
                }
                long backoff = TOKEN_FETCH_BASE_BACKOFF_MS * attempt;
                log.warn("[OAuth] Falha transitória ao obter token (tentativa {}/{}): {}. Retry em {}ms",
                        attempt, TOKEN_FETCH_MAX_ATTEMPTS, e.getMessage(), backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last != null ? last : new IllegalStateException("Falha desconhecida ao obter token");
    }

    /** Detecta erros transitórios (timeout, conexão recusada/resetada, handshake) percorrendo a cadeia de causas. */
    private boolean isTransient(Throwable error) {
        Throwable cur = error;
        while (cur != null) {
            if (cur instanceof java.net.SocketTimeoutException
                    || cur instanceof java.net.ConnectException
                    || cur instanceof java.net.NoRouteToHostException
                    || cur instanceof org.apache.hc.core5.http.ConnectionRequestTimeoutException) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("timed out")
                        || m.contains("timeout")
                        || m.contains("connection reset")
                        || m.contains("connection refused")
                        || m.contains("handshake")) {
                    return true;
                }
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return false;
    }

    private RequestSender resolveSender(BankType bankType,
                                        BankConfiguration cfg,
                                        String tokenUrl,
                                        Boolean forceMtls) {
        if (Boolean.TRUE.equals(forceMtls)) {
            return senderFactory.getMtls(bankType, cfg);
        }
        if (Boolean.FALSE.equals(forceMtls)) {
            return senderFactory.get(bankType, cfg);
        }
        return isPixTokenUrl(tokenUrl)
                ? senderFactory.getMtls(bankType, cfg)
                : senderFactory.get(bankType, cfg);
    }

    private boolean isPixTokenUrl(String tokenUrl) {
        if (tokenUrl == null) return false;
        String u = tokenUrl.toLowerCase();
        return u.contains("pix.api.efipay.com.br") || u.contains("pix-h.api.efipay.com.br");
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
