package com.moonevue.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Transaction;
import com.moonevue.core.entity.TransactionLog;
import com.moonevue.core.enums.Severity;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import com.moonevue.core.repository.BankConfigurationRepository;
import com.moonevue.core.repository.TransactionLogRepository;
import com.moonevue.core.repository.TransactionRepository;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.service.bank.BankIntegration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {
    private final BankIntegrationFactory factory;
    private final ObjectMapper objectMapper;
    private final BankConfigurationRepository bankConfigurationRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository transactionLogRepository;

    public PaymentService(BankIntegrationFactory factory,
                          ObjectMapper objectMapper,
                          BankConfigurationRepository bankConfigurationRepository,
                          TransactionRepository transactionRepository,
                          TransactionLogRepository transactionLogRepository) {
        this.factory = factory;
        this.objectMapper = objectMapper;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.transactionRepository = transactionRepository;
        this.transactionLogRepository = transactionLogRepository;
    }

    @Transactional
    public String create(ChargeRequestDTO request) {
        BankIntegration integration = factory.getIntegration(request.bank());
        BankConfiguration config = bankConfigurationRepository.findById(request.bankConfigurationId())
                .orElseThrow(() -> new IllegalArgumentException("BankConfiguration não encontrada: " + request.bankConfigurationId()));

        // 1) Prepara dados para persistência
        BigDecimal amount = extractAmount(request);
        String payloadJson = serialize(request);

        // 2) Chama o provedor
        String responseJson = integration.processPayment(payloadJson, config);

        // 3) Converte resposta padrão (se possível)
        ChargeResponseDTO resp = null;
        try {
            resp = objectMapper.readValue(responseJson, ChargeResponseDTO.class);
        } catch (Exception ignored) {
            // Se não conseguiu parsear, ainda assim seguiremos com a persistência básica
        }

        // 4) Persiste Transaction
        Transaction tx = new Transaction();
        tx.setTenant(config.getTenant());
        tx.setBankAccount(config.getBankAccount());
        tx.setAmount(amount);
        tx.setType(TransactionType.CHARGE);
        tx.setStatus(TransactionStatus.PENDING); // ficará 'PENDING' até confirmação via webhook/consulta
        tx.setDescription("Cobrança " + request.payment().instrument().name());

        // externalReference = id padronizado (txid no PIX / charge_id no boleto) se houver
        if (resp != null && resp.getId() != null) {
            tx.setExternalReference(resp.getId());
        }

        tx = transactionRepository.save(tx);

        // 5) Persiste TransactionLog (evento de criação no provedor)
        TransactionLog log = new TransactionLog();
        log.setTenant(config.getTenant());
        log.setTransaction(tx);
        log.setEventType("CREATED");
        log.setMessage("Transação criada no provedor " + request.bank().name());
        log.setSeverity(Severity.INFO);

        Map<String, Object> md = new HashMap<>();
        md.put("provider", request.bank().name());
        md.put("instrument", request.payment().instrument().name());
        if (resp != null) {
            md.put("providerId", resp.getId());
            md.put("status", resp.getStatus());
            md.put("amount", resp.getAmount());
            md.put("locId", resp.getLocId());
            md.put("location", resp.getLocation());
        }
        log.setMetadata(md);

        transactionLogRepository.save(log);

        // 6) Retorna a resposta original do provedor (padronizada pela integração)
        return responseJson;
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao serializar request: " + e.getMessage(), e);
        }
    }

    private BigDecimal extractAmount(ChargeRequestDTO request) {
        switch (request.payment().instrument()) {
            case PIX_IMMEDIATE:
                return request.payment().pixImmediate().amount();
            case PIX_DUE:
                return request.payment().pixDue().amountOriginal();
            case BOLETO:
                // Somatório dos itens em centavos -> converte para BigDecimal com 2 casas
                return request.payment().boleto().items().stream()
                        .map(i -> new BigDecimal(i.valueInCents()).movePointLeft(2).multiply(new BigDecimal(i.amount())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            default:
                throw new IllegalArgumentException("Instrumento não suportado para cálculo de amount.");
        }
    }
}
