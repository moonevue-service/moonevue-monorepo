package com.moonevue.gateway.mtls;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.gateway.http.RequestSender;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sender mTLS.
 * Cria o HttpClient com certificado de BankConfiguration a cada chamada
 * (simples; pode ser melhorado com cache de cliente por configuração).
 */
@Component
public class MutualTlsHttpService implements RequestSender {

    private final InsecureCertificateHttpClientFactory clientFactory;

    public MutualTlsHttpService() {
        this.clientFactory = new InsecureCertificateHttpClientFactory();
    }

    public MutualTlsHttpService(InsecureCertificateHttpClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public String send(Method method, String url, String payload) {
        throw new UnsupportedOperationException("Use o overload com BankConfiguration (mTLS).");
    }

    @Override
    public String send(Method method, String url, String payload, Map<String, String> headers) {
        throw new UnsupportedOperationException("Use o overload com BankConfiguration (mTLS).");
    }

    @Override
    public String send(Method method, String url, String payload, Map<String, String> headers, BankConfiguration cfg) throws Exception {
        if (cfg == null || !StringUtils.hasText(cfg.getCertificatePath())) {
            throw new IllegalArgumentException("BankConfiguration com certificado é obrigatório para mTLS.");
        }

        try (CloseableHttpClient httpClient = clientFactory.createFor(cfg)) {
            HttpUriRequestBase req = buildRequest(method, url, payload);

            req.addHeader("Accept", "application/json");
            if (headers != null) headers.forEach(req::addHeader);
            if (payload != null && req.getFirstHeader("Content-Type") == null) {
                req.addHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
            }

            try (var response = httpClient.execute(req)) {
                int status = response.getCode();
                String body = response.getEntity() != null
                        ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                        : "";
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    return body;
                }
                throw new RuntimeException(method + " (mTLS) falhou. HTTP " + status + " - " + body);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro no HTTP mTLS: " + e.getMessage(), e);
        }
    }

    private HttpUriRequestBase buildRequest(Method method, String url, String payload) {
        StringEntity entity = payload != null ? new StringEntity(payload, ContentType.APPLICATION_JSON) : null;
        return switch (method) {
            case GET -> new HttpGet(url);
            case POST -> {
                var r = new HttpPost(url);
                if (entity != null) r.setEntity(entity);
                yield r;
            }
            case PUT -> {
                var r = new HttpPut(url);
                if (entity != null) r.setEntity(entity);
                yield r;
            }
            case PATCH -> {
                var r = new HttpPatch(url);
                if (entity != null) r.setEntity(entity);
                yield r;
            }
            case DELETE -> new HttpDelete(url);
            default -> throw new UnsupportedOperationException("Método não suportado: " + method);
        };
    }
}
