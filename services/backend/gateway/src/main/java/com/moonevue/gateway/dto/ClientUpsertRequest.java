package com.moonevue.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientUpsertRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 120, message = "name deve ter no máximo 120 caracteres")
        String name,

        @NotBlank(message = "cpfCnpj é obrigatório")
        @Size(max = 18, message = "cpfCnpj deve ter no máximo 18 caracteres")
        String cpfCnpj,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        @Size(max = 180, message = "email deve ter no máximo 180 caracteres")
        String email,

        @Size(max = 20, message = "phone deve ter no máximo 20 caracteres")
        String phone
) {
}
