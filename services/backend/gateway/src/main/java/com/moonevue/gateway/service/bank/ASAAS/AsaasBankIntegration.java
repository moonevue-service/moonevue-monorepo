package com.moonevue.gateway.service.bank.ASAAS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.enums.Environment;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.http.HttpRequestException;
import com.moonevue.gateway.http.RequestSenderFactory;
import com.moonevue.gateway.service.bank.ASAAS.config.AsaasBankProperties;
import com.moonevue.gateway.service.bank.ASAAS.config.AsaasConfigKeys;
import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasPaymentRequest;
import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasPaymentResponse;
import com.moonevue.gateway.service.bank.ASAAS.exception.AsaasApiException;
import com.moonevue.gateway.service.bank.ASAAS.mapper.AsaasChargeRequestMapper;
import com.moonevue.gateway.service.bank.ASAAS.mapper.AsaasChargeResponseMapper;
import com.moonevue.gateway.service.bank.BankIntegration;
import com.moonevue.gateway.util.ExtraConfigUtils;
import org.apache.hc.core5.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Integração ASAAS — criação de cobranças via {@code POST /v3/payments}.
 *
 * <p>Segue o mesmo padrão arquitetural da integração EFI:
 * <ul>
 *   <li>implementa {@link BankIntegration} e é registrada na
 *       {@link com.moonevue.gateway.service.BankIntegrationFactory} pelo {@link BankType};</li>
 *   <li>recebe o {@link ChargeRequestDTO} serializado, delega a conversão para os
 *       mappers ({@link AsaasChargeRequestMapper}/{@link AsaasChargeResponseMapper})
 *       e envia via {@link RequestSenderFactory} (sender padrão, sem mTLS);</li>
 *   <li>traduz erros HTTP da ASAAS via {@link AsaasErrorTranslator}.</li>
 * </ul>
 *
 * <p>Autenticação: header {@code access_token} com a chave configurada por
 * variável de ambiente ({@code ASAAS_API_KEY}) ou, por tenant, via
 * {@code extraConfig.asaas.access_token}.
 */
@Component
public class AsaasBankIntegration implements BankIntegration {

    private static final Logger log = LoggerFactory.getLogger(AsaasBankIntegration.class);

    private static final String PAYMENTS_PATH = "/v3/payments";

    private final RequestSenderFactory senderFactory;
    private final ObjectMapper mapper;
    private final AsaasBankProperties properties;
    private final AsaasChargeRequestMapper requestMapper;
    private final AsaasChargeResponseMapper responseMapper;
    private final AsaasErrorTranslator errorTranslator;

    public AsaasBankIntegration(RequestSenderFactory senderFactory,
                                ObjectMapper mapper,
                                AsaasBankProperties properties,
                                AsaasChargeRequestMapper requestMapper,
                                AsaasChargeResponseMapper responseMapper,
                                AsaasErrorTranslator errorTranslator) {
        this.senderFactory = senderFactory;
        this.mapper = mapper;
        this.properties = properties;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.errorTranslator = errorTranslator;
    }

    @Override
    public BankType getBankType() {
        return BankType.ASAAS;
    }

    @Override
    public String processPayment(String payload, BankConfiguration cfg) {
        try {
            ChargeRequestDTO request = mapper.readValue(payload, ChargeRequestDTO.class);
            AsaasPaymentRequest asaasRequest = requestMapper.toAsaasRequest(request, cfg);

            String url = resolveBaseUrl(cfg) + PAYMENTS_PATH;
            String body = mapper.writeValueAsString(asaasRequest);
            Map<String, String> headers = buildHeaders(cfg);

            log.info("[ASAAS] Criando cobrança. configId={} env={} billingType={}",
                    cfg.getId(), cfg.getEnvironment(), asaasRequest.billingType());

            String responseJson = senderFactory.get(BankType.ASAAS, cfg)
                    .send(Method.POST, url, body, headers, cfg);

            AsaasPaymentResponse asaasResponse = mapper.readValue(responseJson, AsaasPaymentResponse.class);
            ChargeResponseDTO out = responseMapper.toChargeResponse(asaasResponse);
            return mapper.writeValueAsString(out);
        } catch (HttpRequestException e) {
            throw errorTranslator.translate(e);
        } catch (AsaasApiException | IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ASAAS] Falha ao processar pagamento. configId={} erro={}",
                    cfg != null ? cfg.getId() : null, e.getMessage(), e);
            throw new RuntimeException("Erro na integração ASAAS: " + e.getMessage(), e);
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
                "[ASAAS] access_token não configurado: defina a variável de ambiente ASAAS_API_KEY "
                        + "ou 'asaas.access_token' no extraConfig da BankConfiguration.");
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
}
