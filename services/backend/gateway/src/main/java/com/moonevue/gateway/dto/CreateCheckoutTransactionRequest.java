package com.moonevue.gateway.dto;

import com.moonevue.core.enums.CheckoutAccessMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCheckoutTransactionRequest(
        @NotNull(message = "bankConfigurationId é obrigatório")
        Long bankConfigurationId,

        @NotNull(message = "amount é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor mínimo R$ 0,01")
        BigDecimal amount,

        @NotBlank(message = "description é obrigatório")
        String description,

        @NotBlank(message = "instrument é obrigatório")
        String instrument,

        Long clientId,

        CheckoutAccessMode checkoutAccessMode,

        String pixKey,

        Integer expiresInHours
) {
}
