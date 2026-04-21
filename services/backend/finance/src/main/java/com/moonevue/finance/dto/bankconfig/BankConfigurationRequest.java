package com.moonevue.finance.dto.bankconfig;

import com.moonevue.core.enums.Environment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record BankConfigurationRequest(
        @NotNull Environment environment,
        Boolean isActive,
        @Size(max = 500) String webhookUrl,
        Map<String, Object> extraConfig
) {}
