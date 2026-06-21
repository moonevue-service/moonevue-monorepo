package com.moonevue.gateway.service.bank.ASAAS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.gateway.http.HttpRequestException;
import com.moonevue.gateway.service.bank.ASAAS.exception.AsaasApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AsaasErrorTranslatorTest {

    private final AsaasErrorTranslator translator = new AsaasErrorTranslator(new ObjectMapper());

    @Test
    void traduz_400_com_detalhes_dos_erros() {
        String body = "{\"errors\":[{\"code\":\"invalid_customer\",\"description\":\"Customer inválido ou não informado\"}]}";

        AsaasApiException ex = translator.translate(new HttpRequestException(400, body, "POST falhou. HTTP 400 - " + body));

        assertThat(ex.getHttpStatus()).isEqualTo(400);
        assertThat(ex.getErrors()).hasSize(1);
        assertThat(ex.getErrors().get(0).code()).isEqualTo("invalid_customer");
        assertThat(ex.getMessage())
                .contains("HTTP 400")
                .contains("invalid_customer")
                .contains("Customer inválido ou não informado");
    }

    @ParameterizedTest
    @CsvSource({
            "400,Requisição inválida enviada à ASAAS",
            "401,Credenciais ASAAS inválidas",
            "403,Acesso negado pela ASAAS",
            "404,Recurso não encontrado na ASAAS",
            "422,Dados não processáveis pela ASAAS",
            "500,Erro interno na ASAAS"
    })
    void mapeia_cada_status_para_mensagem(int status, String expectedFragment) {
        AsaasApiException ex = translator.translate(status, null);

        assertThat(ex.getHttpStatus()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(expectedFragment).contains("HTTP " + status);
    }

    @Test
    void tolera_corpo_de_erro_fora_do_schema() {
        AsaasApiException ex = translator.translate(401, "Unauthorized (texto puro)");

        assertThat(ex.getHttpStatus()).isEqualTo(401);
        assertThat(ex.getErrors()).isEmpty();
        assertThat(ex.getMessage()).contains("HTTP 401");
    }
}
