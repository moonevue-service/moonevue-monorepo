package com.moonevue.gateway.http;

import com.moonevue.gateway.mtls.MutualTlsHttpService;
import com.moonevue.core.enums.BankType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class RequestSenderFactory {
    private final Map<BankType, RequestSender> map = new EnumMap<>(BankType.class);
    private final RequestSender defaultSender;

    public RequestSenderFactory(DefaultRequestSender defaultRequestSender, MutualTlsHttpService mtlsHttpService) {
        this.defaultSender = defaultRequestSender;
        map.put(BankType.EFI, mtlsHttpService);
    }

    public RequestSender get(BankType bankType) {
        return map.getOrDefault(bankType, defaultSender);
    }
}
