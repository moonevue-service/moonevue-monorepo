package com.moonevue.gateway.dto;

import java.time.OffsetDateTime;

public record ClientSummaryDTO(
        Long id,
        String name,
        String cpfCnpj,
        String email,
        String phone,
        String status,
        OffsetDateTime createdAt
) {
}
