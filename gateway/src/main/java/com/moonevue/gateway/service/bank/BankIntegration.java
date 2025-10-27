package com.moonevue.gateway.service.bank;

import com.moonevue.core.enums.BankType;

public interface BankIntegration {

    BankType getBankType();

    String processPayment(String payload);
}
