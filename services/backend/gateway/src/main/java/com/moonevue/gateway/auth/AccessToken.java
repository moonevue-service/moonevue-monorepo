package com.moonevue.gateway.auth;

import java.time.Instant;

public class AccessToken {
    private final String token;
    private final String tokenType; // normalmente "Bearer"
    private final Instant expiresAt;

    public AccessToken(String token, String tokenType, Instant expiresAt) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public String getTokenType() { return tokenType; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        // margem de segurança de 30s
        return Instant.now().isAfter(expiresAt.minusSeconds(30));
    }
}
