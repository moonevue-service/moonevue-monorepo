package com.moonevue.gateway.service.bank.ASAAS.exception;

import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasErrorResponse;

import java.util.List;

/**
 * Exceção interna que representa um erro retornado pela API ASAAS.
 *
 * <p>Centraliza o mapeamento dos códigos HTTP da ASAAS
 * (400/401/403/404/422/500) para a semântica interna da aplicação. A mensagem
 * inclui o status HTTP para que os handlers existentes do gateway exponham o
 * detalhe ao chamador.
 */
public class AsaasApiException extends RuntimeException {

    private final int httpStatus;
    private final transient List<AsaasErrorResponse.AsaasError> errors;

    public AsaasApiException(int httpStatus, List<AsaasErrorResponse.AsaasError> errors, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public List<AsaasErrorResponse.AsaasError> getErrors() {
        return errors;
    }
}
