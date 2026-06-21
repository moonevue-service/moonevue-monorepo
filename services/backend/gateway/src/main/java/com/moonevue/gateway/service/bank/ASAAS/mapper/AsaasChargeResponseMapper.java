package com.moonevue.gateway.service.bank.ASAAS.mapper;

import com.moonevue.core.enums.BankType;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasPaymentResponse;
import org.springframework.stereotype.Component;

/**
 * Converte a resposta da ASAAS ({@link AsaasPaymentResponse}) para o modelo
 * interno padronizado {@link ChargeResponseDTO}.
 *
 * <p>Mapeamentos principais:
 * <ul>
 *   <li>{@code id} (pay_...) &rarr; {@code id};</li>
 *   <li>{@code status} &rarr; {@code status};</li>
 *   <li>{@code value} &rarr; {@code amount} (string);</li>
 *   <li>{@code invoiceUrl} &rarr; {@code location}/{@code link} (página da fatura);</li>
 *   <li>{@code bankSlipUrl} &rarr; {@code billetLink}/{@code pdfLink} (boleto).</li>
 * </ul>
 *
 * <p>O QR Code copia-e-cola do PIX não é retornado pelo endpoint de criação; é
 * obtido por chamada dedicada ({@code GET /v3/payments/{id}/pixQrCode}), fora do
 * escopo inicial. A página da fatura ({@code invoiceUrl}) permite o pagamento.
 */
@Component
public class AsaasChargeResponseMapper {

    public ChargeResponseDTO toChargeResponse(AsaasPaymentResponse response) {
        if (response == null) {
            throw new IllegalStateException("[ASAAS] Resposta nula do provedor.");
        }

        ChargeResponseDTO out = new ChargeResponseDTO();
        out.setProvider(BankType.ASAAS);
        out.setKind(kindFromBillingType(response.billingType()));
        out.setId(response.id());
        out.setStatus(response.status());
        out.setAmount(response.value() != null ? response.value().toPlainString() : null);
        out.setDueDate(response.dueDate());

        out.setLocation(response.invoiceUrl());
        out.setLink(response.invoiceUrl());

        if (response.bankSlipUrl() != null) {
            out.setBilletLink(response.bankSlipUrl());
            out.setPdfLink(response.bankSlipUrl());
        }

        return out;
    }

    private String kindFromBillingType(String billingType) {
        if (billingType == null) {
            return "asaas_payment";
        }
        return switch (billingType.toUpperCase()) {
            case "PIX" -> "asaas_pix";
            case "BOLETO" -> "asaas_boleto";
            case "CREDIT_CARD" -> "asaas_credit_card";
            default -> "asaas_payment";
        };
    }
}
