package com.moonevue.gateway.mtls;

import com.moonevue.core.entity.BankConfiguration;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;

public class InsecureCertificateHttpClientFactory {
    /**
     * ATENÇÃO: Confia em QUALQUER certificado de servidor e desabilita verificação de hostname.
     * Uso apenas em desenvolvimento.
     * Após testes e finalização do sistema em desenvolvimento -> Troque InsecureCertificateHttpClientFactory por CertificateHttpClientFactory e construa uma fábrica que carregue um truststore próprio (.p12/.jks) e remova o NoopHostnameVerifier.
     */
    public CloseableHttpClient createFor(BankConfiguration bankConfiguration) {
        try {
            Path p12Path = Path.of(bankConfiguration.getCertificatePath());
            if (!Files.exists(p12Path)) {
                throw new IllegalArgumentException("Arquivo de certificado do cliente não encontrado: " + p12Path);
            }
            char[] keyPass = bankConfiguration.getCertificatePassword() != null
                    ? bankConfiguration.getCertificatePassword().toCharArray()
                    : new char[0];

            KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(p12Path.toFile())) {
                clientKeyStore.load(fis, keyPass);
            }

            // TrustAll + NoopHostnameVerifier (inseguro)
            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(clientKeyStore, keyPass)       // certificado do cliente (mTLS)
                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)   // confia em todos os servidores
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    new String[]{"TLSv1.3", "TLSv1.2"},
                    null,
                    NoopHostnameVerifier.INSTANCE
            );

            var cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            var requestConfig = org.apache.hc.client5.http.config.RequestConfig.custom()
                    .setConnectTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(15)))
                    .setResponseTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(30)))
                    .build();

            return HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultRequestConfig(requestConfig)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Falha ao criar HttpClient (inseguro) com mTLS: " + e.getMessage(), e);
        }
    }
}
