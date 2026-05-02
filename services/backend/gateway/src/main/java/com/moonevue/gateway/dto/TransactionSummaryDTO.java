package com.moonevue.gateway.dto;

import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionSummaryDTO(
        Long id,
        BigDecimal amount,
        TransactionStatus status,
        TransactionType type,
        String description,
        String externalReference,
        String bank,
        OffsetDateTime createdAt
) {}
