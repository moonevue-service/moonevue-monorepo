package com.moonevue.gateway.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload enviado pelo cliente final ao confirmar o pagamento no checkout.
 *
 * <p>Os dados do pagador ({@code payerName}/{@code payerDocument}) NÃO são mais
 * obrigatórios de forma fixa: a exigência depende do provedor e do tipo de
 * cobrança (ver {@code DebtorRequirementPolicy}). Ex.: PIX imediato dispensa o
 * devedor, enquanto PIX com vencimento e boleto o exigem.
 */
public record CheckoutPayRequest(

        @NotBlank(message = "instrument é obrigatório")
        String instrument,

        /** Nome do pagador. Obrigatório conforme a política do instrumento. */
        String payerName,

        /** CPF ou CNPJ do pagador. Obrigatório conforme a política do instrumento. */
        String payerDocument,

        /** E-mail do pagador (opcional mas recomendado) */
        String payerEmail,

        String payerPhone,

        /** Chave PIX (apenas para PIX_IMMEDIATE; pode ser omitida se já configurada no link) */
        String pixKey
) {}
