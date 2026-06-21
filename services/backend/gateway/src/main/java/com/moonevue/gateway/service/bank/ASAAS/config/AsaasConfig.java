package com.moonevue.gateway.service.bank.ASAAS.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra as propriedades da integração ASAAS como bean gerenciado.
 */
@Configuration
@EnableConfigurationProperties(AsaasBankProperties.class)
public class AsaasConfig {
}
