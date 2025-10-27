package com.moonevue.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.gateway.dto.PaymentRequestDTO;
import com.moonevue.gateway.service.bank.BankIntegration;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final BankIntegrationFactory factory;
    private final ObjectMapper objectMapper;

    public PaymentService(BankIntegrationFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    public String create(PaymentRequestDTO request) {
        // Decide o banco por requisição
        BankIntegration integration = factory.getIntegration(request.bank());

        // Aqui você decide a estratégia:
        // 1) Serializar o DTO genérico e deixar a integração montar o payload final
        //    (útil se cada integração sabe transformar o "genérico" para o formato do banco)
        String payloadJson = serialize(request);
        return integration.processPayment(payloadJson);
    }

    private String serialize(PaymentRequestDTO request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao serializar request: " + e.getMessage(), e);
        }
    }
}
