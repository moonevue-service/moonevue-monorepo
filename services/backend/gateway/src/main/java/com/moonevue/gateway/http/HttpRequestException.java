package com.moonevue.gateway.http;

/**
 * Exceção tipada lançada pelos {@link RequestSender} em respostas HTTP não 2xx.
 *
 * <p>Carrega o código de status e o corpo da resposta do provedor de forma
 * estruturada, permitindo que cada integração mapeie o erro para a sua própria
 * semântica (ex.: ASAAS 400/401/403/404/422/500) sem depender de parsing frágil
 * da mensagem.
 *
 * <p>A mensagem mantém o mesmo formato textual usado historicamente
 * ({@code "<MÉTODO> falhou. HTTP <status> - <body>"}) para preservar
 * compatibilidade com fluxos que já inspecionam a mensagem (ex.: tratamento de
 * erro de validação da EFI no PaymentController).
 */
public class HttpRequestException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public HttpRequestException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
