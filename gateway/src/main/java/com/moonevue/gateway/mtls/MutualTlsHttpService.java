package com.moonevue.gateway.mtls;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.gateway.http.RequestSender;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serviço simples para realizar requisições GET/POST (JSON) com mTLS usando o certificado do Contractor.
 * Cria um HttpClient por chamada (simples e seguro). Se precisar de alto throughput,
 * considere cachear/reutilizar clientes por certificado e fechá-los no shutdown da aplicação.
 */
@Component
public class MutualTlsHttpService implements RequestSender {

    private final InsecureCertificateHttpClientFactory clientFactory;

    public MutualTlsHttpService() {
        this.clientFactory = new InsecureCertificateHttpClientFactory();
    }
    /*
    * Trocar InsecureCertificateHttpClientFactory por CertificateHttpClientFactory para produção
    * */
    public MutualTlsHttpService(InsecureCertificateHttpClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public String get(BankConfiguration bankConfiguration, String url, Map<String, String> headers) {
        try (CloseableHttpClient httpClient = clientFactory.createFor(bankConfiguration)) {
            HttpGet request = new HttpGet(url);
            request.addHeader("Accept", "application/json");
            if (headers != null) headers.forEach(request::addHeader);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int status = response.getCode();
                String body = response.getEntity() != null
                        ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                        : "";
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    return body;
                }
                throw new RuntimeException("GET falhou. HTTP " + status + " - " + body);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro no GET mTLS: " + e.getMessage(), e);
        }
    }

    public String postJson(BankConfiguration bankConfiguration, String url, String jsonBody, Map<String, String> headers) {
        try (CloseableHttpClient httpClient = clientFactory.createFor(bankConfiguration)) {
            HttpPost request = new HttpPost(url);
            request.addHeader("Accept", "application/json");
            request.addHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
            if (headers != null) headers.forEach(request::addHeader);
            if (jsonBody != null) {
                request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
            }

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int status = response.getCode();
                String body = response.getEntity() != null
                        ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                        : "";
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    return body;
                }
                throw new RuntimeException("POST falhou. HTTP " + status + " - " + body);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro no POST mTLS: " + e.getMessage(), e);
        }
    }

    @Override
    public String send(Method mothod, String url, String payload) throws Exception {
        return "";
    }
}
