package com.moonevue.gateway.service.bank.ASAAS.mapper;

import com.moonevue.core.enums.BankType;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasPaymentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AsaasChargeResponseMapperTest {

    private final AsaasChargeResponseMapper mapper = new AsaasChargeResponseMapper();

    @Test
    void mapeia_resposta_pix_para_modelo_interno() {
        AsaasPaymentResponse response = new AsaasPaymentResponse(
                "payment", "pay_080225913252", "PENDING", "PIX",
                new BigDecimal("129.90"), new BigDecimal("124.90"),
                "2026-06-10", "2026-06-10", "2026-06-01",
                "Pedido 1", "056984", "cus_G7Dvo4iphUNk",
                "https://www.asaas.com/i/080225913252", "00005101",
                null, null, null, false);

        ChargeResponseDTO out = mapper.toChargeResponse(response);

        assertThat(out.getProvider()).isEqualTo(BankType.ASAAS);
        assertThat(out.getKind()).isEqualTo("asaas_pix");
        assertThat(out.getId()).isEqualTo("pay_080225913252");
        assertThat(out.getStatus()).isEqualTo("PENDING");
        assertThat(out.getAmount()).isEqualTo("129.90");
        assertThat(out.getDueDate()).isEqualTo("2026-06-10");
        assertThat(out.getLocation()).isEqualTo("https://www.asaas.com/i/080225913252");
        assertThat(out.getLink()).isEqualTo("https://www.asaas.com/i/080225913252");
        assertThat(out.getBilletLink()).isNull();
    }

    @Test
    void mapeia_resposta_boleto_com_bankSlipUrl() {
        AsaasPaymentResponse response = new AsaasPaymentResponse(
                "payment", "pay_999", "PENDING", "BOLETO",
                new BigDecimal("200.00"), null,
                "2026-08-01", null, null,
                null, null, "cus_X",
                "https://www.asaas.com/i/999", null,
                "https://www.asaas.com/b/pdf/999", "6453", null, false);

        ChargeResponseDTO out = mapper.toChargeResponse(response);

        assertThat(out.getKind()).isEqualTo("asaas_boleto");
        assertThat(out.getBilletLink()).isEqualTo("https://www.asaas.com/b/pdf/999");
        assertThat(out.getPdfLink()).isEqualTo("https://www.asaas.com/b/pdf/999");
        assertThat(out.getAmount()).isEqualTo("200.00");
    }
}
