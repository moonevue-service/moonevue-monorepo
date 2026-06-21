package com.moonevue.gateway.service.bank.ASAAS.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Resposta da ASAAS ao criar uma cobrança ({@code POST /v3/payments}).
 *
 * <p>Espelha um subconjunto relevante do schema {@code PaymentGetResponseDTO}.
 * Campos desconhecidos são ignorados para tolerar a evolução da API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPaymentResponse(
        String object,
        String id,
        String status,
        String billingType,
        BigDecimal value,
        BigDecimal netValue,
        String dueDate,
        String originalDueDate,
        String dateCreated,
        String description,
        String externalReference,
        String customer,
        String invoiceUrl,
        String invoiceNumber,
        String bankSlipUrl,
        String nossoNumero,
        String transactionReceiptUrl,
        Boolean deleted
) {}
