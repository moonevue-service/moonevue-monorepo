package com.moonevue.gateway.http;

import com.moonevue.core.entity.BankConfiguration;
import org.apache.hc.core5.http.Method;

import java.util.Map;

/**
 * Adiciona suporte a headers e contexto por BankConfiguration.
 */
public interface RequestSender {
    String send(Method method, String url, String payload) throws Exception;

    String send(Method method, String url, String payload, Map<String, String> headers) throws Exception;

    // Preferível quando precisar mTLS/timeout por banco
    String send(Method method, String url, String payload, Map<String, String> headers, BankConfiguration cfg) throws Exception;
}
