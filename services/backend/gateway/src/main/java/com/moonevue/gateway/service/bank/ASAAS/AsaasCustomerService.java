package com.moonevue.gateway.service.bank.ASAAS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Client;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.enums.Environment;
import com.moonevue.gateway.http.HttpRequestException;
import com.moonevue.gateway.http.RequestSenderFactory;
import com.moonevue.gateway.service.bank.ASAAS.config.AsaasBankProperties;
import com.moonevue.gateway.service.bank.ASAAS.config.AsaasConfigKeys;
import com.moonevue.gateway.util.ExtraConfigUtils;
import org.apache.hc.core5.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class AsaasCustomerService {

    private static final Logger log = LoggerFactory.getLogger(AsaasCustomerService.class);

    private final RequestSenderFactory senderFactory;
    private final ObjectMapper mapper;
    private final AsaasBankProperties properties;

    public AsaasCustomerService(RequestSenderFactory senderFactory,
                                ObjectMapper mapper,
                                AsaasBankProperties properties) {
        this.senderFactory = senderFactory;
        this.mapper = mapper;
        this.properties = properties;
    }

    public String findCustomerIdByDocument(BankConfiguration cfg, String document) {
        String normalizedDoc = onlyDigits(document);
        if (normalizedDoc == null) {
            return null;
        }

        try {
            String query = URLEncoder.encode(normalizedDoc, StandardCharsets.UTF_8);
            String url = resolveBaseUrl(cfg) + "/v3/customers?cpfCnpj=" + query + "&limit=1";
            String response = senderFactory.get(BankType.ASAAS, cfg)
                    .send(Method.GET, url, null, buildHeaders(cfg), cfg);

            JsonNode root = mapper.readTree(response == null ? "{}" : response);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return null;
            }
            String customerId = text(data.get(0), "id");
            if (customerId != null) {
                log.info("[ASAAS] Cliente encontrado por documento doc={} customerId={}", maskedDocument(normalizedDoc), customerId);
            }
            return customerId;
        } catch (HttpRequestException e) {
            log.warn("[ASAAS] Falha ao buscar cliente por documento. status={} body={}", e.getStatusCode(), e.getResponseBody());
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao consultar cliente na ASAAS: " + e.getMessage(), e);
        }
    }

    public String createCustomer(BankConfiguration cfg, Client client) {
        if (client == null) {
            throw new IllegalArgumentException("Cliente é obrigatório para criação na ASAAS");
        }

        String document = onlyDigits(client.getCpfCnpj());
        if (document == null) {
            throw new IllegalArgumentException("Documento do cliente inválido para cadastro na ASAAS");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", client.getName());
            payload.put("cpfCnpj", document);

            if (client.getEmail() != null && !client.getEmail().isBlank()) {
                payload.put("email", client.getEmail().trim());
            }

            String phone = normalizePhone(client.getPhone());
            if (phone != null) {
                payload.put("mobilePhone", phone);
            }

            String url = resolveBaseUrl(cfg) + "/v3/customers";
            String body = mapper.writeValueAsString(payload);
            String response = senderFactory.get(BankType.ASAAS, cfg)
                    .send(Method.POST, url, body, buildHeaders(cfg), cfg);

            String customerId = text(mapper.readTree(response == null ? "{}" : response), "id");
            if (customerId == null) {
                throw new IllegalStateException("ASAAS não retornou id de cliente");
            }

            log.info("[ASAAS] Cliente criado customerId={} clientId={}", customerId, client.getId());
            return customerId;
        } catch (HttpRequestException e) {
            log.warn("[ASAAS] Falha ao criar cliente. status={} body={}", e.getStatusCode(), e.getResponseBody());
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar cliente na ASAAS: " + e.getMessage(), e);
        }
    }

    private Map<String, String> buildHeaders(BankConfiguration cfg) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("access_token", resolveApiKey(cfg));
        if (properties.getUserAgent() != null && !properties.getUserAgent().isBlank()) {
            headers.put("User-Agent", properties.getUserAgent());
        }
        return headers;
    }

    private String resolveApiKey(BankConfiguration cfg) {
        String fromConfig = cfg != null
                ? ExtraConfigUtils.getString(cfg.getExtraConfig(), AsaasConfigKeys.ACCESS_TOKEN, null)
                : null;
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig.trim();
        }

        String fromProperties = properties.getApiKey();
        if (fromProperties != null && !fromProperties.isBlank()) {
            return fromProperties.trim();
        }

        throw new IllegalStateException(
                "[ASAAS] access_token não configurado: defina ASAAS_API_KEY ou asaas.access_token no extraConfig.");
    }

    private String resolveBaseUrl(BankConfiguration cfg) {
        boolean production = cfg != null && cfg.getEnvironment() == Environment.PRODUCTION;
        String defaultBase = production ? properties.getProductionBaseUrl() : properties.getSandboxBaseUrl();
        String base = cfg != null
                ? ExtraConfigUtils.getString(cfg.getExtraConfig(), AsaasConfigKeys.BASE_URL, defaultBase)
                : defaultBase;
        return stripTrailingSlash(base);
    }

    private String stripTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String onlyDigits(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.length() != 11 && normalized.length() != 14) {
            return null;
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String out = value.asText();
        return out == null || out.isBlank() ? null : out;
    }

    private String maskedDocument(String document) {
        if (document == null || document.length() < 4) {
            return "***";
        }
        return "***" + document.substring(document.length() - 4);
    }
}
