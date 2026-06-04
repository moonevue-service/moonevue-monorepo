package com.moonevue.core.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequiredEnvironmentValidatorTest {

    @Test
    void validate_nao_deve_lancar_quando_variaveis_existirem() {
        assertDoesNotThrow(() -> RequiredEnvironmentValidator.validate(
                "finance",
                Map.of(
                        "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost/moonevue",
                        "SPRING_DATASOURCE_USERNAME", "moonevue",
                        "SPRING_DATASOURCE_PASSWORD", "secret",
                        "AUTH_BASE_URL", "http://auth:8081"
                ),
                "SPRING_DATASOURCE_URL",
                "SPRING_DATASOURCE_USERNAME",
                "SPRING_DATASOURCE_PASSWORD",
                "AUTH_BASE_URL"
        ));
    }

    @Test
    void validate_deve_lancar_mensagem_amigavel_quando_variaveis_faltarem() {
        var exception = assertThrows(IllegalStateException.class, () -> RequiredEnvironmentValidator.validate(
                "gateway",
                Map.of("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost/moonevue"),
                "SPRING_DATASOURCE_URL",
                "SPRING_DATASOURCE_USERNAME",
                "SPRING_DATASOURCE_PASSWORD",
                "AUTH_BASE_URL"
        ));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("gateway"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("SPRING_DATASOURCE_USERNAME"));
    }
}