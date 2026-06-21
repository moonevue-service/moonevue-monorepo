package com.moonevue.gateway.service.bank.ASAAS.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades globais da integração ASAAS.
 *
 * <p>Configuradas via {@code application.yml} (prefixo {@code banks.asaas}) e,
 * preferencialmente, por variável de ambiente — nunca com valores fixos no
 * código:
 *
 * <pre>
 * banks:
 *   asaas:
 *     api-key: ${ASAAS_API_KEY:}
 *     sandbox-base-url: https://api-sandbox.asaas.com
 *     production-base-url: https://api.asaas.com
 *     user-agent: Moonevue/1.0
 * </pre>
 *
 * <p>A {@code api-key} é o {@code access_token} enviado no header de autenticação
 * de todas as chamadas. Cada {@code BankConfiguration} pode sobrescrevê-la via
 * {@code extraConfig.asaas.access_token}, permitindo chaves por tenant.
 */
@ConfigurationProperties(prefix = "banks.asaas")
public class AsaasBankProperties {

    /** access_token da conta ASAAS (origem: variável de ambiente ASAAS_API_KEY). */
    private String apiKey;

    /** Base URL do ambiente de homologação (sandbox). */
    private String sandboxBaseUrl = "https://api-sandbox.asaas.com";

    /** Base URL do ambiente de produção. */
    private String productionBaseUrl = "https://api.asaas.com";

    /** User-Agent recomendado pela ASAAS para identificar a aplicação. */
    private String userAgent = "Moonevue/1.0";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSandboxBaseUrl() {
        return sandboxBaseUrl;
    }

    public void setSandboxBaseUrl(String sandboxBaseUrl) {
        this.sandboxBaseUrl = sandboxBaseUrl;
    }

    public String getProductionBaseUrl() {
        return productionBaseUrl;
    }

    public void setProductionBaseUrl(String productionBaseUrl) {
        this.productionBaseUrl = productionBaseUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
