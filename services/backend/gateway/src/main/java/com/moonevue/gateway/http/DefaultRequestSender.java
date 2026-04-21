package com.moonevue.gateway.http;

import com.moonevue.core.entity.BankConfiguration;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Sender padrão (sem mTLS).
 * Constrói um HttpClient por chamada. Se quiser otimizar, você pode reaproveitar cliente/connection manager.
 */
@Component
public class DefaultRequestSender implements RequestSender {

    @Override
    public String send(Method method, String url, String payload) throws Exception {
        return send(method, url, payload, Map.of());
    }

    @Override
    public String send(Method method, String url, String payload, Map<String, String> headers) throws Exception {
        var requestConfig = org.apache.hc.client5.http.config.RequestConfig.custom()
                .setConnectTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(15)))
                .setResponseTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(30)))
                .build();

        try (CloseableHttpClient http = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpUriRequestBase req = buildRequest(method, url, payload);
            // headers
            req.addHeader("Accept", "application/json");
            if (headers != null) {
                headers.forEach(req::addHeader);
            }
            // Content-Type se tiver body e não foi setado
            if (payload != null && req.getFirstHeader("Content-Type") == null) {
                req.addHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
            }

            try (var resp = http.execute(req)) {
                int status = resp.getCode();
                String body = resp.getEntity() != null
                        ? EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8)
                        : "";
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) return body;
                throw new RuntimeException(method + " falhou. HTTP " + status + " - " + body);
            }
        }
    }

    @Override
    public String send(Method method, String url, String payload, Map<String, String> headers, BankConfiguration cfg) throws Exception {
        // Este sender ignora cfg (não mTLS). A fábrica escolherá o sender mTLS quando necessário.
        return send(method, url, payload, headers);
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
            case DELETE -> new HttpDelete(url); // sem body
            default -> throw new UnsupportedOperationException("Método não suportado: " + method);
        };
    }
}
