package com.moonevue.gateway.service.bank.ASAAS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.enums.Environment;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.http.HttpRequestException;
import com.moonevue.gateway.http.RequestSender;
import com.moonevue.gateway.http.RequestSenderFactory;
import com.moonevue.gateway.service.bank.ASAAS.config.AsaasBankProperties;
import com.moonevue.gateway.service.bank.ASAAS.exception.AsaasApiException;
import com.moonevue.gateway.service.bank.ASAAS.mapper.AsaasChargeRequestMapper;
import com.moonevue.gateway.service.bank.ASAAS.mapper.AsaasChargeResponseMapper;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsaasBankIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private RequestSenderFactory senderFactory;
    private RequestSender sender;
    private AsaasBankProperties properties;
    private AsaasBankIntegration integration;

    @BeforeEach
    void setUp() {
        senderFactory = mock(RequestSenderFactory.class);
        sender = mock(RequestSender.class);
        properties = new AsaasBankProperties();
        properties.setApiKey("env-api-key");
        when(senderFactory.get(eq(BankType.ASAAS), any())).thenReturn(sender);

        integration = new AsaasBankIntegration(
                senderFactory,
                objectMapper,
                properties,
                new AsaasChargeRequestMapper(),
                new AsaasChargeResponseMapper(),
                new AsaasErrorTranslator(objectMapper));
    }

    private BankConfiguration config(Map<String, Object> extraConfig) {
        BankConfiguration cfg = new BankConfiguration();
        cfg.setId(7L);
        cfg.setEnvironment(Environment.SANDBOX);
        cfg.setExtraConfig(extraConfig);
        return cfg;
    }

    private String payloadPixImmediate() throws Exception {
        ChargeRequestDTO charge = new ChargeRequestDTO(BankType.ASAAS, 7L,
                new ChargeRequestDTO.Payment(
                        ChargeRequestDTO.Instrument.PIX_IMMEDIATE,
                        new ChargeRequestDTO.PixImmediate(3600, null, null, null,
                                new BigDecimal("129.90"), "Pedido 1", null),
                        null, null));
        return objectMapper.writeValueAsString(charge);
    }

    @Test
    void getBankType_retorna_ASAAS() {
        assertThat(integration.getBankType()).isEqualTo(BankType.ASAAS);
    }

    @Test
    void processPayment_envia_para_v3_payments_com_access_token_e_mapeia_resposta() throws Exception {
        String asaasResponse = "{\"object\":\"payment\",\"id\":\"pay_123\",\"status\":\"PENDING\","
                + "\"billingType\":\"PIX\",\"value\":129.90,\"dueDate\":\"2026-06-20\","
                + "\"invoiceUrl\":\"https://www.asaas.com/i/123\"}";
        when(sender.send(eq(Method.POST), anyString(), anyString(), any(), any())).thenReturn(asaasResponse);

        BankConfiguration cfg = config(Map.of("asaas", Map.of("customer", "cus_X")));
        String result = integration.processPayment(payloadPixImmediate(), cfg);

        var json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo("pay_123");
        assertThat(json.get("status").asText()).isEqualTo("PENDING");
        assertThat(json.get("provider").asText()).isEqualTo("ASAAS");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(sender).send(eq(Method.POST), urlCaptor.capture(), anyString(),
                headersCaptor.capture(), any());

        assertThat(urlCaptor.getValue()).isEqualTo("https://api-sandbox.asaas.com/v3/payments");
        assertThat(headersCaptor.getValue()).containsEntry("access_token", "env-api-key");
        assertThat(headersCaptor.getValue()).containsEntry("Content-Type", "application/json");
    }

    @Test
    void processPayment_usa_access_token_do_extraConfig_quando_presente() throws Exception {
        when(sender.send(eq(Method.POST), anyString(), anyString(), any(), any()))
                .thenReturn("{\"id\":\"pay_1\",\"status\":\"PENDING\",\"billingType\":\"PIX\"}");

        BankConfiguration cfg = config(Map.of("asaas",
                Map.of("customer", "cus_X", "access_token", "tenant-token")));
        integration.processPayment(payloadPixImmediate(), cfg);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(sender).send(eq(Method.POST), anyString(), anyString(),
                headersCaptor.capture(), any());
        assertThat(headersCaptor.getValue()).containsEntry("access_token", "tenant-token");
    }

    @Test
    void processPayment_traduz_erro_http_para_AsaasApiException() throws Exception {
        String errorBody = "{\"errors\":[{\"code\":\"invalid_customer\",\"description\":\"Customer inválido\"}]}";
        when(sender.send(eq(Method.POST), anyString(), anyString(), any(), any()))
                .thenThrow(new HttpRequestException(400, errorBody, "POST falhou. HTTP 400 - " + errorBody));

        BankConfiguration cfg = config(Map.of("asaas", Map.of("customer", "cus_X")));

        assertThatThrownBy(() -> integration.processPayment(payloadPixImmediate(), cfg))
                .isInstanceOf(AsaasApiException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("invalid_customer");
    }

    @Test
    void processPayment_sem_apiKey_lanca_IllegalStateException() throws Exception {
        properties.setApiKey(null);
        BankConfiguration cfg = config(Map.of("asaas", Map.of("customer", "cus_X")));

        assertThatThrownBy(() -> integration.processPayment(payloadPixImmediate(), cfg))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
    }

    @Test
    void processPayment_sem_customer_lanca_IllegalArgumentException() throws Exception {
        BankConfiguration cfg = config(Map.of());

        assertThatThrownBy(() -> integration.processPayment(payloadPixImmediate(), cfg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer");
    }
}
