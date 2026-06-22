package com.moonevue.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Resposta pública e estável de uma cobrança.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicChargeResponse(
        String id,
        String status,
        String method,
        String provider,
        BigDecimal amount,
        String currency,
        String externalReference,
        Pix pix,
        Boleto boleto,
        String createdAt
) {
    public record Pix(String copyPaste, String location, Integer expiresInSeconds) {}

    public record Boleto(String line, String pdfUrl, String invoiceUrl) {}
}
