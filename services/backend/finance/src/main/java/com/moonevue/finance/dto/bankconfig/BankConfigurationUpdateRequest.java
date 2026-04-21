package com.moonevue.finance.dto.bankconfig;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record BankConfigurationUpdateRequest(
        Boolean isActive,
        @Size(max = 500) String webhookUrl,
        Map<String, Object> extraConfig
) {}
