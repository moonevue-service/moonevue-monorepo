package com.moonevue.gateway.mtls;

import com.moonevue.core.entity.BankConfiguration;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;

public class CertificateHttpClientFactory {
    public CloseableHttpClient createFor(BankConfiguration bankConfiguration) {
        try {
            Path p12Path = Path.of(bankConfiguration.getCertificatePath());
            if (!Files.exists(p12Path)) {
                throw new IllegalArgumentException("Arquivo de certificado não encontrado: " + p12Path);
            }

            // Tenta null e empty char[] para certificados sem senha
            char[] rawPassword = bankConfiguration.getCertificatePassword() != null
                    ? bankConfiguration.getCertificatePassword().toCharArray()
                    : null;
            char[][] candidates = rawPassword != null
                    ? new char[][]{rawPassword, null, new char[0]}
                    : new char[][]{null, new char[0]};

            KeyStore keyStore = null;
            char[] usedPassword = null;
            Exception lastEx = null;
            for (char[] attempt : candidates) {
                try {
                    KeyStore ks = KeyStore.getInstance("PKCS12");
                    try (FileInputStream fis = new FileInputStream(p12Path.toFile())) {
                        ks.load(fis, attempt);
                    }
                    keyStore = ks;
                    usedPassword = attempt;
                    break;
                } catch (Exception e) {
                    lastEx = e;
                }
            }
            if (keyStore == null) {
                throw new IllegalArgumentException("Falha ao carregar certificado PKCS12: " +
                        (lastEx != null ? lastEx.getMessage() : "erro desconhecido"), lastEx);
            }

            // Monta o SSLContext com material de chave do cliente
            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(keyStore, usedPassword) // chave privada do cliente
                    // .loadTrustMaterial(TrustAllStrategy.INSTANCE) // NÃO recomendado: confia em todos
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    new String[]{"TLSv1.3", "TLSv1.2"}, // Protocolos permitidos
                    null, // cipher suites default
                    null    // HostnameVerifier default (seguro). Para desabilitar (não recomendado):
            );

            var cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            // Ajuste de timeouts básicos
            var requestConfig = org.apache.hc.client5.http.config.RequestConfig.custom()
                    .setConnectTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(15)))
                    .setResponseTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(30)))
                    .build();

            return HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultRequestConfig(requestConfig)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Falha ao criar HttpClient com certificado: " + e.getMessage(), e);
        }
    }
}
