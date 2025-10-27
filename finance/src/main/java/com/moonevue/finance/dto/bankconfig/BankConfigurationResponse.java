package com.moonevue.finance.dto.bankconfig;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.Environment;

import java.time.OffsetDateTime;
import java.util.Map;

public record BankConfigurationResponse(
        Long id,
        Long contractorId,
        Long bankAccountId,
        Environment environment,
        Boolean isActive,
        String webhookUrl,
        Map<String, Object> extraConfig,
        String certificatePathMasked,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastSyncAt
) {
    public static BankConfigurationResponse from(BankConfiguration bc) {
        String masked = bc.getCertificatePath() == null ? null : bc.getCertificatePath().replaceAll("(?s).+?([^/\\\\]+)$", "***$1");
        return new BankConfigurationResponse(
                bc.getId(),
                bc.getContractor().getId(),
                bc.getBankAccount().getId(),
                bc.getEnvironment(),
                bc.getIsActive(),
                bc.getWebhookUrl(),
                bc.getExtraConfig(),
                masked,
                bc.getCreatedAt(),
                bc.getUpdatedAt(),
                bc.getLastSyncAt()
        );
    }
}
