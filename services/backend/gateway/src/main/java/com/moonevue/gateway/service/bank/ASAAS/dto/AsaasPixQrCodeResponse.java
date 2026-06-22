package com.moonevue.gateway.service.bank.ASAAS.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Resposta da ASAAS ao obter o QR Code PIX de uma cobrança
 * ({@code GET /v3/payments/{id}/pixQrCode}).
 *
 * <ul>
 *   <li>{@code encodedImage} — imagem do QR Code em base64 (PNG, sem prefixo data URI);</li>
 *   <li>{@code payload} — código copia-e-cola (BR Code);</li>
 *   <li>{@code expirationDate} — validade do QR Code.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPixQrCodeResponse(
        String encodedImage,
        String payload,
        String expirationDate,
        Boolean success
) {}
