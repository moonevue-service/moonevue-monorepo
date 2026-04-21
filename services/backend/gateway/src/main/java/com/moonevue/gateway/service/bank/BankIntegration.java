package com.moonevue.gateway.service.bank;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;

public interface BankIntegration {

    BankType getBankType();

    @Deprecated
    default String processPayment(String payload) {
        throw new UnsupportedOperationException("Use processPayment(payload, bankConfiguration)");
    }

    String processPayment(String payload, BankConfiguration bankConfiguration);
}
