package com.moonevue.gateway.service;


import com.moonevue.gateway.service.bank.BankIntegration;
import com.moonevue.core.enums.BankType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class BankIntegrationFactory {
    private final Map<BankType, BankIntegration> integrations;

    public BankIntegrationFactory(List<BankIntegration> implementations) {
        this.integrations = new EnumMap<>(BankType.class);
        for (BankIntegration impl : implementations) {
            // sobrescreve caso haja duplicata; é possível validar aqui
            integrations.put(impl.getBankType(), impl);
        }
    }

    public BankIntegration getIntegration(BankType type) {
        BankIntegration integration = integrations.get(type);
        if (integration == null) {
            throw new IllegalArgumentException("Banco não suportado: " + type);
        }
        return integration;
    }
}
