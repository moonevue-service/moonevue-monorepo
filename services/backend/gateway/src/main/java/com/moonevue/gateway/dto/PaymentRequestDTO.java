package com.moonevue.gateway.dto;

import com.moonevue.core.enums.BankType;

import java.math.BigDecimal;

public record PaymentRequestDTO(
        Long bankConfigurationId,
        BankType bank,
        String clientId,
        String description,
        BigDecimal amount
) {}