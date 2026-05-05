package com.moonevue.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload enviado pelo cliente final ao confirmar o pagamento no checkout.
 */
public record CheckoutPayRequest(

        @NotBlank(message = "instrument é obrigatório")
        String instrument,

        @NotBlank(message = "payerName é obrigatório")
        String payerName,

        @NotBlank(message = "payerDocument é obrigatório (CPF ou CNPJ)")
        String payerDocument,

        /** E-mail do pagador (opcional mas recomendado) */
        String payerEmail,

        String payerPhone,

        /** Chave PIX (apenas para PIX_IMMEDIATE; pode ser omitida se já configurada no link) */
        String pixKey
) {}
