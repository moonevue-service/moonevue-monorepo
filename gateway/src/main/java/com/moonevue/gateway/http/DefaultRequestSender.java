package com.moonevue.gateway.http;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class DefaultRequestSender implements RequestSender {

    @Override
    public String send(Method method, String url, String payload) throws Exception {
        var requestConfig = org.apache.hc.client5.http.config.RequestConfig.custom()
                .setConnectTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(15)))
                .setResponseTimeout(org.apache.hc.core5.util.Timeout.of(Duration.ofSeconds(30)))
                .build();

        try (CloseableHttpClient http = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            switch (method) {
                case GET -> {
                    var req = new HttpGet(url);
                    req.addHeader("Accept", "application/json");
                    try (CloseableHttpResponse resp = http.execute(req)) {
                        int status = resp.getCode();
                        String body = resp.getEntity() != null
                                ? EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8)
                                : "";
                        if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) return body;
                        throw new RuntimeException("GET falhou. HTTP " + status + " - " + body);
                    }
                }
                case POST -> {
                    var req = new HttpPost(url);
                    req.addHeader("Accept", "application/json");
                    req.addHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
                    if (payload != null) req.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
                    try (CloseableHttpResponse resp = http.execute(req)) {
                        int status = resp.getCode();
                        String body = resp.getEntity() != null
                                ? EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8)
                                : "";
                        if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) return body;
                        throw new RuntimeException("POST falhou. HTTP " + status + " - " + body);
                    }
                }
                default -> throw new UnsupportedOperationException("Método não suportado: " + method);
            }
        }
    }
}
