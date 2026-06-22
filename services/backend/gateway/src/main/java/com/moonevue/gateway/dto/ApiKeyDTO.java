package com.moonevue.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moonevue.core.entity.ApiKey;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Representação de uma API Key para a tela de gestão. Nunca contém o segredo.
 * {@code plaintextKey} só é preenchido na criação/rotação (exibição única).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyDTO(
        Long id,
        String name,
        String keyId,
        String keyPrefix,
        String environment,
        List<String> scopes,
        String status,
        OffsetDateTime lastUsedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        String plaintextKey
) {
    public static ApiKeyDTO from(ApiKey apiKey) {
        return from(apiKey, null);
    }

    public static ApiKeyDTO from(ApiKey apiKey, String plaintextKey) {
        String envPart = apiKey.getEnvironment() != null
                ? apiKey.getEnvironment().name().toLowerCase()
                : "test";
        List<String> scopes = (apiKey.getScopes() == null || apiKey.getScopes().isBlank())
                ? List.of()
                : Arrays.stream(apiKey.getScopes().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new ApiKeyDTO(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyId(),
                "mvk_" + envPart + "_" + apiKey.getKeyId(),
                apiKey.getEnvironment() != null ? apiKey.getEnvironment().name() : null,
                scopes,
                apiKey.getStatus() != null ? apiKey.getStatus().name() : null,
                apiKey.getLastUsedAt(),
                apiKey.getExpiresAt(),
                apiKey.getCreatedAt(),
                plaintextKey
        );
    }
}
