package com.moonevue.gateway.service.bank.ASAAS.config;

/**
 * Convenção de chaves usadas no {@code extraConfig} de {@code BankConfiguration}
 * para a integração ASAAS. Seguem o padrão de namespace por ponto consumido por
 * {@link com.moonevue.gateway.util.ExtraConfigUtils}.
 */
public final class AsaasConfigKeys {

    private AsaasConfigKeys() {}

    /** Namespace raiz das chaves ASAAS dentro do extraConfig. */
    public static final String NS = "asaas";

    /** access_token específico do tenant (sobrescreve banks.asaas.api-key). Path: {@code asaas.access_token}. */
    public static final String ACCESS_TOKEN = NS + ".access_token";

    /** Base URL alternativa. Path: {@code asaas.baseUrl}. */
    public static final String BASE_URL = NS + ".baseUrl";

    /** Customer id (cus_xxx) padrão da configuração. Path: {@code asaas.customer}. */
    public static final String CUSTOMER = NS + ".customer";

    /** Override do billingType (UNDEFINED/BOLETO/CREDIT_CARD/PIX). Path: {@code asaas.billingType}. */
    public static final String BILLING_TYPE = NS + ".billingType";

    /** Prefixo de identificadores de cliente na ASAAS. */
    public static final String CUSTOMER_ID_PREFIX = "cus_";
}
