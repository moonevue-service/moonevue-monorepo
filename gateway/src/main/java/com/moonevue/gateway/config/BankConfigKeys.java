package com.moonevue.gateway.config;

/**
 * Convenção de chaves no extraConfig do BankConfiguration para integrações bancárias.
 */
public final class BankConfigKeys {
    private BankConfigKeys() {}

    // Namespaces
    public static final String PIX_NS = "pix";
    public static final String CHARGES_NS = "charges";

    // Comuns
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String SCOPE = "scope";
    public static final String BASE_URL = "baseUrl";
    public static final String TOKEN_URL = "tokenUrl";

    // PIX específicos
    public static final String PIX_KEY = "pixKey";
}
