package com.moonevue.gateway.service.bank.EFI.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "banks.efi")
public class EfiBankProperties {
    // Defina estes no application.yml:
    // banks:
    //   efi:
    //     production: false
    //     client-id: YOUR-CLIENT-ID
    //     client-secret: YOUR-CLIENT-SECRET

    private boolean production = false;
    private String clientId;
    private String clientSecret;
    private String scope; // opcional

    public boolean isProduction() { return production; }
    public void setProduction(boolean production) { this.production = production; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
}
