package com.moonevue.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutIdentifyRequest(
        @NotBlank(message = "document é obrigatório")
        String document
) {
}
