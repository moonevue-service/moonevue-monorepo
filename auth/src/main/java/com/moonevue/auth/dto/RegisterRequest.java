package com.moonevue.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterRequest {

    // Dados do Tenant (empresa)
    @NotBlank
    private String tenantName;

    @NotBlank
    private String tenantDocument; // CNPJ/CPF (normalizar no service, se necessário)

    // Dados do usuário (admin inicial)
    @Email @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String confirmPassword;
}