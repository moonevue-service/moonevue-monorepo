package com.moonevue.core.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class RequiredEnvironmentValidator {

    private RequiredEnvironmentValidator() {
    }

    public static void validate(String serviceName, Map<String, String> environment, String... requiredVariables) {
        List<String> missing = Arrays.stream(requiredVariables)
                .filter(variable -> {
                    String value = environment.get(variable);
                    return value == null || value.isBlank();
                })
                .sorted()
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variables for " + serviceName + ": " + String.join(", ", missing)
                            + ". Copy the matching .env.example file and set these values before starting the service."
            );
        }
    }
}