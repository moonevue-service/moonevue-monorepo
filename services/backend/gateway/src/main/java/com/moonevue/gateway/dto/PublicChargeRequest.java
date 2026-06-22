package com.moonevue.gateway.dto;

import com.moonevue.core.enums.BankType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato público e estável de criação de cobrança via API.
 * Desacoplado dos DTOs específicos de provedor ({@link ChargeRequestDTO}).
 */
public record PublicChargeRequest(
        Method method,
        BankType bank,
        Long bankConfigurationId,
        BigDecimal amount,
        LocalDate dueDate,
        String description,
        String externalReference,
        String pixKey,
        Customer customer
) {
    public enum Method {
        PIX_IMMEDIATE,
        PIX_DUE,
        BOLETO
    }

    public record Customer(
            String name,
            String document,
            String email,
            String phone
    ) {}
}
