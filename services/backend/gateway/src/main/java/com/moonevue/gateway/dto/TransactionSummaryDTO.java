package com.moonevue.gateway.dto;

import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionSummaryDTO(
        Long id,
        BigDecimal amount,
        TransactionStatus status,
        TransactionType type,
        String description,
        String externalReference,
        UUID checkoutToken,
        String checkoutUrl,
        OffsetDateTime checkoutExpiresAt,
        String checkoutInstrument,
        Long clientId,
        String clientName,
        String checkoutAccessMode,
        String bank,
        OffsetDateTime createdAt
        , String boletoInvoiceUrl,
        String boletoPdfUrl
) {}
