package com.moonevue.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClientUpsertRequest(
        @NotBlank(message = "name é obrigatório")
        String name,

        @NotBlank(message = "cpfCnpj é obrigatório")
        String cpfCnpj,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        String phone
) {
}
