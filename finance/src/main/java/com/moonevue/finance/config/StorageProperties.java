package com.moonevue.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    /**
     * Diretório base onde os certificados serão armazenados.
     * Ex.: /opt/gateway/certs (não commitado no repositório).
     */
    private String certsDir = "storage/certs";

    public String getCertsDir() {
        return certsDir;
    }

    public void setCertsDir(String certsDir) {
        this.certsDir = certsDir;
    }
}
