package com.moonevue.gateway.service.bank.ASAAS.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Corpo de erro padrão da ASAAS (schema {@code ErrorResponseDTO}), retornado em
 * respostas 400/401/403/404/422 etc.
 *
 * <pre>
 * { "errors": [ { "code": "invalid_customer", "description": "Customer inválido" } ] }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasErrorResponse(List<AsaasError> errors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsaasError(String code, String description) {}
}
