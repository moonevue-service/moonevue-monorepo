package com.moonevue.gateway.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ChargeSummaryDTO(
        Long id,
        String provider,
        String providerChargeId,
        String paymentMethod,
        String status,
        BigDecimal amountTotal,
        BigDecimal amountPaid,
        String pixCopyPaste,
        String boletoLine,
        String boletoInvoiceUrl,
        String boletoPdfUrl,
        OffsetDateTime createdAt
) {
}
