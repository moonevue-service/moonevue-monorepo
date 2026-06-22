package com.moonevue.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter simples em memória (token bucket por janela fixa de 1 minuto), por chave de API.
 *
 * Adequado para uma instância. Em ambiente multi-instância deve evoluir para um backend
 * distribuído (ex.: Redis) — ver roadmap da fase 3 em docs/evolution/api-publica-integracoes.md.
 */
@Service
public class RateLimiterService {

    private final int limitPerMinute;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiterService(@Value("${moonevue.gateway.api-keys.rate-limit-per-minute:120}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    public Result tryAcquire(String bucketKey) {
        long currentMinute = System.currentTimeMillis() / 60_000L;
        Window window = windows.compute(bucketKey, (k, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new Window(currentMinute, 1);
            }
            existing.count++;
            return existing;
        });

        int used = window.count;
        boolean allowed = used <= limitPerMinute;
        int remaining = Math.max(0, limitPerMinute - used);
        long resetEpochSeconds = (currentMinute + 1) * 60L;
        return new Result(allowed, limitPerMinute, remaining, resetEpochSeconds);
    }

    public record Result(boolean allowed, int limit, int remaining, long resetEpochSeconds) {}

    private static final class Window {
        final long minute;
        int count;

        Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
