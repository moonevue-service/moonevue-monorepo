package com.moonevue.gateway.service.bank.ASAAS.model;

/**
 * Formas de pagamento aceitas pela ASAAS no campo {@code billingType} ao criar
 * uma cobrança ({@code POST /v3/payments}).
 *
 * <p>Conforme o enum {@code PaymentSaveRequestBillingType} da especificação
 * oficial da ASAAS.
 */
public enum AsaasBillingType {
    UNDEFINED,
    BOLETO,
    CREDIT_CARD,
    PIX
}
