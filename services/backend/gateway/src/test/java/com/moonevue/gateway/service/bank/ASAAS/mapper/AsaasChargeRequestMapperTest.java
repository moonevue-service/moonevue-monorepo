package com.moonevue.gateway.service.bank.ASAAS.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.Environment;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasPaymentRequest;
import com.moonevue.gateway.service.bank.ASAAS.model.AsaasBillingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsaasChargeRequestMapperTest {

    private final AsaasChargeRequestMapper mapper = new AsaasChargeRequestMapper();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private BankConfiguration config(Map<String, Object> extraConfig) {
        BankConfiguration cfg = new BankConfiguration();
        cfg.setId(10L);
        cfg.setEnvironment(Environment.SANDBOX);
        cfg.setExtraConfig(extraConfig);
        return cfg;
    }

    private ChargeRequestDTO pixImmediate(ChargeRequestDTO.PixImmediate p) {
        return new ChargeRequestDTO(com.moonevue.core.enums.BankType.ASAAS, 10L,
                new ChargeRequestDTO.Payment(ChargeRequestDTO.Instrument.PIX_IMMEDIATE, p, null, null));
    }

    @Test
    void pixImmediate_mapeia_billingType_PIX_value_e_dueDate_hoje() {
        var charge = pixImmediate(new ChargeRequestDTO.PixImmediate(
                3600, null, null, null, new BigDecimal("129.90"), "Pedido 1", null));

        AsaasPaymentRequest result = mapper.toAsaasRequest(charge,
                config(Map.of("asaas", Map.of("customer", "cus_G7Dvo4iphUNk"))));

        assertThat(result.customer()).isEqualTo("cus_G7Dvo4iphUNk");
        assertThat(result.billingType()).isEqualTo(AsaasBillingType.PIX);
        assertThat(result.value()).isEqualByComparingTo("129.90");
        assertThat(result.dueDate()).isEqualTo(LocalDate.now());
        assertThat(result.description()).isEqualTo("Pedido 1");
    }

    @Test
    void pixImmediate_resolve_customer_do_request_quando_prefixo_cus() {
        var charge = pixImmediate(new ChargeRequestDTO.PixImmediate(
                3600, "cus_REQUEST123", null, null, new BigDecimal("10.00"), null, null));

        AsaasPaymentRequest result = mapper.toAsaasRequest(charge, config(Map.of()));

        assertThat(result.customer()).isEqualTo("cus_REQUEST123");
    }

    @Test
    void pixDue_mapeia_dueDate_multa_juros_e_desconto() {
        var p = new ChargeRequestDTO.PixDue(
                "txid", LocalDate.of(2026, 7, 10), 30,
                null, null, null, null, null, null, null,
                new BigDecimal("200.00"),
                "2.5", "1.0",
                LocalDate.of(2026, 7, 5), "5.0",
                "Cobrança", "cus_DUE");
        var charge = new ChargeRequestDTO(com.moonevue.core.enums.BankType.ASAAS, 10L,
                new ChargeRequestDTO.Payment(ChargeRequestDTO.Instrument.PIX_DUE, null, p, null));

        AsaasPaymentRequest result = mapper.toAsaasRequest(charge, config(Map.of()));

        assertThat(result.billingType()).isEqualTo(AsaasBillingType.PIX);
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(result.fine().value()).isEqualByComparingTo("2.5");
        assertThat(result.fine().type()).isEqualTo(AsaasPaymentRequest.FineType.PERCENTAGE);
        assertThat(result.interest().value()).isEqualByComparingTo("1.0");
        assertThat(result.discount().value()).isEqualByComparingTo("5.0");
        assertThat(result.discount().dueDateLimitDays()).isEqualTo(5);
        assertThat(result.discount().type()).isEqualTo(AsaasPaymentRequest.DiscountType.PERCENTAGE);
    }

    @Test
    void boleto_mapeia_value_total_e_daysAfterDueDate() {
        var boleto = new ChargeRequestDTO.Boleto(
                List.of(new ChargeRequestDTO.Boleto.Item("Item", 5000, 2)),
                new ChargeRequestDTO.Boleto.Customer("Fulano", "12345678901", null, null, null, null),
                LocalDate.of(2026, 8, 1),
                new ChargeRequestDTO.Boleto.Configurations(null, null, 3, null),
                "Mensagem");
        var charge = new ChargeRequestDTO(com.moonevue.core.enums.BankType.ASAAS, 10L,
                new ChargeRequestDTO.Payment(ChargeRequestDTO.Instrument.BOLETO, null, null, boleto));

        AsaasPaymentRequest result = mapper.toAsaasRequest(charge,
                config(Map.of("asaas", Map.of("customer", "cus_BOLETO"))));

        assertThat(result.billingType()).isEqualTo(AsaasBillingType.BOLETO);
        assertThat(result.value()).isEqualByComparingTo("100.00");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.daysAfterDueDateToRegistrationCancellation()).isEqualTo(3);
    }

    @Test
    void semCustomer_lanca_IllegalArgumentException() {
        var charge = pixImmediate(new ChargeRequestDTO.PixImmediate(
                3600, "12345678901", null, "Fulano", new BigDecimal("10.00"), null, null));

        assertThatThrownBy(() -> mapper.toAsaasRequest(charge, config(Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer");
    }

    @Test
    void value_invalido_lanca_IllegalArgumentException() {
        var charge = pixImmediate(new ChargeRequestDTO.PixImmediate(
                3600, null, null, null, BigDecimal.ZERO, null, null));

        assertThatThrownBy(() -> mapper.toAsaasRequest(charge,
                config(Map.of("asaas", Map.of("customer", "cus_X")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void serializa_dueDate_no_formato_yyyy_MM_dd_e_omite_nulos() throws Exception {
        var charge = pixImmediate(new ChargeRequestDTO.PixImmediate(
                3600, null, null, null, new BigDecimal("50.00"), null, null));

        AsaasPaymentRequest result = mapper.toAsaasRequest(charge,
                config(Map.of("asaas", Map.of("customer", "cus_X"))));
        String body = json.writeValueAsString(result);

        assertThat(body).contains("\"dueDate\":\"" + LocalDate.now() + "\"");
        assertThat(body).contains("\"billingType\":\"PIX\"");
        assertThat(body).contains("\"customer\":\"cus_X\"");
        assertThat(body).doesNotContain("\"description\"");
        assertThat(body).doesNotContain("\"fine\"");
        assertThat(body).doesNotContain("\"interest\"");
    }
}
