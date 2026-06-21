package com.moonevue.finance.analytics.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache de curta duração para dashboards analíticos (tempo quase real).
 * A chave inclui o {@code tenantId}, garantindo isolamento entre tenants.
 */
@Configuration
@EnableCaching
public class AnalyticsCacheConfig {

    public static final String DASHBOARD_CACHE = "analytics-dashboard";

    @Bean
    public CacheManager analyticsCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(DASHBOARD_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(2_000));
        return manager;
    }
}
