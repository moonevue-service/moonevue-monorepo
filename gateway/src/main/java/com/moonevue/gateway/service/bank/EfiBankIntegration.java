package com.moonevue.gateway.service.bank;

import com.moonevue.gateway.http.RequestSenderFactory;
import com.moonevue.core.enums.BankType;
import org.apache.hc.core5.http.Method;
import org.springframework.stereotype.Component;

@Component
public class EfiBankIntegration implements BankIntegration {
    private final String homologationPath = "https://pix-h.api.efipay.com.br/v2";
    private final String productionPath = "https://pix.api.efipay.com.br/v2";

    private final RequestSenderFactory senderFactory;

    public EfiBankIntegration(RequestSenderFactory senderFactory) {
        this.senderFactory = senderFactory;
    }

    @Override
    public BankType getBankType() {
        return BankType.EFI;
    }

    @Override
    public String processPayment(String payload) {
        String url = homologationPath + "/payments";
        try {
            return senderFactory.get(BankType.EFI).send(Method.POST, url, payload);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar pagamento EFI", e);
        }
    }
}
