package com.moonevue.gateway.service.bank.ASAAS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.enums.Environment;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.http.DefaultRequestSender;
import com.moonevue.gateway.http.DefaultRequestSenderFactory;
import com.moonevue.gateway.mtls.MutualTlsHttpService;
import com.moonevue.gateway.service.bank.ASAAS.config.AsaasBankProperties;
import com.moonevue.gateway.service.bank.ASAAS.exception.AsaasApiException;
import com.moonevue.gateway.service.bank.ASAAS.mapper.AsaasChargeRequestMapper;
import com.moonevue.gateway.service.bank.ASAAS.mapper.AsaasChargeResponseMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de integração real contra o ambiente sandbox da ASAAS.
 *
 * <p>Executa somente quando a variável de ambiente {@code ASAAS_API_KEY} está
 * definida (evita falhas em CI/offline). O nome termina em {@code IT} para não
 * ser executado pelo surefire no {@code mvn test} padrão. Para rodar:
 *
 * <pre>
 * ASAAS_API_KEY=&lt;chave_sandbox&gt; ./mvnw -pl gateway \
 *   -Dtest=AsaasBankIntegrationSandboxIT -DfailIfNoTests=false test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "ASAAS_API_KEY", matches = ".+")
class AsaasBankIntegrationSandboxIT {

    private static final String SANDBOX = "https://api-sandbox.asaas.com";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static String apiKey;
    private AsaasBankIntegration integration;

    @BeforeAll
    static void readKey() {
        apiKey = System.getenv("ASAAS_API_KEY");
    }

    private AsaasBankIntegration buildIntegration() {
        AsaasBankProperties props = new AsaasBankProperties();
        props.setApiKey(apiKey);
        DefaultRequestSenderFactory factory =
                new DefaultRequestSenderFactory(new DefaultRequestSender(), new MutualTlsHttpService());
        return new AsaasBankIntegration(
                factory,
                objectMapper,
                props,
                new AsaasChargeRequestMapper(),
                new AsaasChargeResponseMapper(),
                new AsaasErrorTranslator(objectMapper));
    }

    private BankConfiguration sandboxConfig(String customerId) {
        BankConfiguration cfg = new BankConfiguration();
        cfg.setId(1L);
        cfg.setEnvironment(Environment.SANDBOX);
        cfg.setExtraConfig(Map.of("asaas", Map.of("customer", customerId)));
        return cfg;
    }

    /** Cria um cliente no sandbox e devolve o id (cus_...) para uso nas cobranças. */
    private String createSandboxCustomer() throws Exception {
        String body = "{\"name\":\"Cliente Teste Moonevue\",\"cpfCnpj\":\"24971563792\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(SANDBOX + "/v3/customers"))
                .header("access_token", apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Moonevue/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        JsonNode json = objectMapper.readTree(response.body());
        return json.get("id").asText();
    }

    @Test
    void cria_cobranca_pix_no_sandbox_e_retorna_id_e_status() throws Exception {
        integration = buildIntegration();
        String customerId = createSandboxCustomer();

        ChargeRequestDTO charge = new ChargeRequestDTO(BankType.ASAAS, 1L,
                new ChargeRequestDTO.Payment(
                        ChargeRequestDTO.Instrument.PIX_DUE,
                        null,
                        new ChargeRequestDTO.PixDue(
                                null, LocalDate.now().plusDays(3), null,
                                null, null, null, null, null, null, null,
                                new BigDecimal("99.90"),
                                null, null, null, null,
                                "Cobrança de teste sandbox", customerId),
                        null));

        String result = integration.processPayment(objectMapper.writeValueAsString(charge), sandboxConfig(customerId));

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).startsWith("pay_");
        assertThat(json.get("status").asText()).isNotBlank();
        assertThat(json.get("provider").asText()).isEqualTo("ASAAS");
    }

    @Test
    void cobranca_com_customer_inexistente_retorna_AsaasApiException() throws Exception {
        integration = buildIntegration();

        ChargeRequestDTO charge = new ChargeRequestDTO(BankType.ASAAS, 1L,
                new ChargeRequestDTO.Payment(
                        ChargeRequestDTO.Instrument.PIX_DUE,
                        null,
                        new ChargeRequestDTO.PixDue(
                                null, LocalDate.now().plusDays(3), null,
                                null, null, null, null, null, null, null,
                                new BigDecimal("99.90"),
                                null, null, null, null,
                                "Cobrança inválida", "cus_invalido_000"),
                        null));

        assertThatThrownBy(() ->
                integration.processPayment(objectMapper.writeValueAsString(charge), sandboxConfig("cus_invalido_000")))
                .isInstanceOf(AsaasApiException.class);
    }
}
