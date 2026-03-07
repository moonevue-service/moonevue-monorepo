package com.moonevue.gateway.auth;

public class OAuthClientCredentials {
    private final String clientId;
    private final String clientSecret;
    private final String scope; // pode ser null

    public OAuthClientCredentials(String clientId, String clientSecret, String scope) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getScope() { return scope; }
}
