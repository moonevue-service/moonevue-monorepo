package com.moonevue.auth.config;

import com.moonevue.core.config.RequiredEnvironmentValidator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class StartupEnvironmentConfig {

    @Bean
    ApplicationRunner validateAuthEnvironment() {
        return args -> RequiredEnvironmentValidator.validate(
                "auth",
                System.getenv(),
                "SPRING_DATASOURCE_URL",
                "SPRING_DATASOURCE_USERNAME",
                "SPRING_DATASOURCE_PASSWORD",
                "AUTH_INTERNAL_TOKEN"
        );
    }
}