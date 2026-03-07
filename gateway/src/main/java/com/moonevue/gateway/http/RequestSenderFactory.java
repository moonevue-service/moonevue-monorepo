package com.moonevue.gateway.http;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;

public interface RequestSenderFactory {
    RequestSender get(BankType type);
    RequestSender get(BankType type, BankConfiguration cfg);
    RequestSender getMtls(BankType type, BankConfiguration cfg);
}
