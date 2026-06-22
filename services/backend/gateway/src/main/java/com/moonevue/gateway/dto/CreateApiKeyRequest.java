package com.moonevue.gateway.dto;

import com.moonevue.core.enums.ApiKeyEnvironment;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateApiKeyRequest(
        String name,
        ApiKeyEnvironment environment,
        List<String> scopes,
        OffsetDateTime expiresAt
) {}
