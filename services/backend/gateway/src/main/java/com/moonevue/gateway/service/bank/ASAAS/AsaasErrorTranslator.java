package com.moonevue.gateway.service.bank.ASAAS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.gateway.http.HttpRequestException;
import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasErrorResponse;
import com.moonevue.gateway.service.bank.ASAAS.exception.AsaasApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Traduz falhas HTTP da ASAAS ({@link HttpRequestException}) para
 * {@link AsaasApiException}, mapeando explicitamente os códigos de status
 * exigidos (400, 401, 403, 404, 422, 500) para mensagens internas claras.
 *
 * <p>O corpo de erro da ASAAS segue o schema {@code ErrorResponseDTO}
 * ({@code {"errors":[{"code","description"}]}}); quando presente, os detalhes
 * são incluídos na mensagem.
 */
@Component
public class AsaasErrorTranslator {

    private static final Logger log = LoggerFactory.getLogger(AsaasErrorTranslator.class);

    private final ObjectMapper objectMapper;

    public AsaasErrorTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AsaasApiException translate(HttpRequestException error) {
        return translate(error.getStatusCode(), error.getResponseBody());
    }

    public AsaasApiException translate(int status, String responseBody) {
        List<AsaasErrorResponse.AsaasError> errors = parseErrors(responseBody);
        String detail = formatErrors(errors);

        String reason = switch (status) {
            case 400 -> "Requisição inválida enviada à ASAAS";
            case 401 -> "Credenciais ASAAS inválidas (access_token ausente ou incorreto)";
            case 403 -> "Acesso negado pela ASAAS para esta operação";
            case 404 -> "Recurso não encontrado na ASAAS";
            case 422 -> "Dados não processáveis pela ASAAS";
            case 500 -> "Erro interno na ASAAS";
            default -> "Falha na comunicação com a ASAAS";
        };

        String message = detail.isBlank()
                ? "[ASAAS] " + reason + " (HTTP " + status + ")"
                : "[ASAAS] " + reason + " (HTTP " + status + "): " + detail;

        log.warn("[ASAAS] erro HTTP {} ao criar cobrança: {}", status, detail.isBlank() ? "(sem detalhes)" : detail);
        return new AsaasApiException(status, errors, message);
    }

    private List<AsaasErrorResponse.AsaasError> parseErrors(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            AsaasErrorResponse parsed = objectMapper.readValue(responseBody, AsaasErrorResponse.class);
            return parsed.errors() == null ? List.of() : parsed.errors();
        } catch (Exception e) {
            log.debug("[ASAAS] corpo de erro não segue o schema esperado: {}", e.getMessage());
            return List.of();
        }
    }

    private String formatErrors(List<AsaasErrorResponse.AsaasError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .map(e -> {
                    String code = e.code() != null ? e.code() : "erro";
                    String description = e.description() != null ? e.description() : "";
                    return description.isBlank() ? code : code + ": " + description;
                })
                .collect(Collectors.joining(" | "));
    }
}
