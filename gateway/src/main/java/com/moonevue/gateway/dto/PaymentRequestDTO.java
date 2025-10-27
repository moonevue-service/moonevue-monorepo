package com.moonevue.gateway.dto;

import com.moonevue.core.enums.BankType;

import java.math.BigDecimal;

public record PaymentRequestDTO(
        BankType bank,
        String clientId,
        String description,
        BigDecimal amount
) {}