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

    private final RequestSenderFactory senderFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, AccessToken> cache = new ConcurrentHashMap<>();

    public OAuthTokenService(RequestSenderFactory senderFactory, ObjectMapper objectMapper) {
        this.senderFactory = senderFactory;
        this.objectMapper = objectMapper;
    }

    public AccessToken getTokenFor(BankType bankType, String tokenUrl, OAuthClientCredentials creds, BankConfiguration cfg) {
        String cacheKey = bankType.name() + "|" + cfg.getId() + "|" + tokenUrl;
        AccessToken cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        AccessToken fresh = fetchToken(bankType, tokenUrl, creds, cfg);
        cache.put(cacheKey, fresh);
        return fresh;
    }

    private AccessToken fetchToken(BankType bankType, String tokenUrl, OAuthClientCredentials creds, BankConfiguration cfg) {
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

            // Se o tokenUrl é da API PIX, força mTLS; caso contrário, usa sender padrão
            RequestSender sender = isPixTokenUrl(tokenUrl)
                    ? senderFactory.getMtls(bankType, cfg)
                    : senderFactory.get(bankType, cfg);

            String resp = sender.send(Method.POST, tokenUrl, body, headers, cfg);

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

    private boolean isPixTokenUrl(String tokenUrl) {
        if (tokenUrl == null) return false;
        String u = tokenUrl.toLowerCase();
        return u.contains("pix.api.efipay.com.br") || u.contains("pix-h.api.efipay.com.br");
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
