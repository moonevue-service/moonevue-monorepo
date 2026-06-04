package com.moonevue.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Client;
import com.moonevue.core.entity.Transaction;
import com.moonevue.core.entity.TransactionLog;
import com.moonevue.core.enums.CheckoutAccessMode;
import com.moonevue.core.enums.Severity;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import com.moonevue.core.repository.BankConfigurationRepository;
import com.moonevue.core.repository.ClientRepository;
import com.moonevue.core.repository.TransactionLogRepository;
import com.moonevue.core.repository.TransactionRepository;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.dto.CreateCheckoutTransactionRequest;
import com.moonevue.gateway.dto.TransactionSummaryDTO;
import com.moonevue.gateway.service.bank.BankIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final BankIntegrationFactory factory;
    private final ObjectMapper objectMapper;
    private final BankConfigurationRepository bankConfigurationRepository;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository transactionLogRepository;

    public PaymentService(BankIntegrationFactory factory,
                          ObjectMapper objectMapper,
                          BankConfigurationRepository bankConfigurationRepository,
                          ClientRepository clientRepository,
                          TransactionRepository transactionRepository,
                          TransactionLogRepository transactionLogRepository) {
        this.factory = factory;
        this.objectMapper = objectMapper;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.clientRepository = clientRepository;
        this.transactionRepository = transactionRepository;
        this.transactionLogRepository = transactionLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionSummaryDTO> listTransactions(Long tenantId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return transactionRepository.findByTenantId(tenantId, pageable)
                .map(t -> new TransactionSummaryDTO(
                        t.getId(),
                        t.getAmount(),
                        t.getStatus(),
                        t.getType(),
                        t.getDescription(),
                        t.getExternalReference(),
                        t.getCheckoutToken(),
                        t.getCheckoutToken() != null ? frontendUrl + "/checkout/" + t.getCheckoutToken() : null,
                        t.getCheckoutExpiresAt(),
                        t.getCheckoutInstrument(),
                        t.getClient() != null ? t.getClient().getId() : null,
                        t.getClient() != null ? t.getClient().getName() : null,
                        t.getCheckoutAccessMode() != null ? t.getCheckoutAccessMode().name() : null,
                        t.getBankAccount().getBank() != null ? t.getBankAccount().getBank().name() : null,
                        t.getCreatedAt()
                ));
    }

    @Transactional
    public TransactionSummaryDTO createCheckoutDraft(Long tenantId, CreateCheckoutTransactionRequest request) {
        BankConfiguration config = bankConfigurationRepository.findById(request.bankConfigurationId())
            .orElseThrow(() -> new IllegalArgumentException("BankConfiguration não encontrada: " + request.bankConfigurationId()));

        if (!config.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("BankConfiguration não pertence ao tenant");
        }

        int expiresInHours = request.expiresInHours() != null && request.expiresInHours() > 0
            ? Math.min(request.expiresInHours(), 168)
            : 24;

        Transaction tx = new Transaction();
        tx.setTenant(config.getTenant());
        tx.setBankAccount(config.getBankAccount());
        tx.setBankConfiguration(config);
        tx.setAmount(request.amount());
        tx.setType(TransactionType.CHARGE);
        tx.setStatus(TransactionStatus.CHECKOUT_OPEN);
        tx.setDescription(request.description());
        tx.setCheckoutInstrument(request.instrument());
        CheckoutAccessMode accessMode = request.checkoutAccessMode() != null
            ? request.checkoutAccessMode()
            : CheckoutAccessMode.PUBLIC;
        tx.setCheckoutAccessMode(accessMode);
        tx.setCheckoutPixKey(request.pixKey());
        tx.setCheckoutToken(UUID.randomUUID());
        tx.setCheckoutExpiresAt(OffsetDateTime.now().plusHours(expiresInHours));

        if (request.clientId() != null) {
            Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado: " + request.clientId()));
            if (!client.getTenant().getId().equals(tenantId)) {
                throw new IllegalArgumentException("Cliente não pertence ao tenant");
            }
            tx.setClient(client);
        }

        tx = transactionRepository.save(tx);

        return new TransactionSummaryDTO(
            tx.getId(),
            tx.getAmount(),
            tx.getStatus(),
            tx.getType(),
            tx.getDescription(),
            tx.getExternalReference(),
            tx.getCheckoutToken(),
            frontendUrl + "/checkout/" + tx.getCheckoutToken(),
            tx.getCheckoutExpiresAt(),
            tx.getCheckoutInstrument(),
            tx.getClient() != null ? tx.getClient().getId() : null,
            tx.getClient() != null ? tx.getClient().getName() : null,
            tx.getCheckoutAccessMode() != null ? tx.getCheckoutAccessMode().name() : null,
            tx.getBankAccount().getBank() != null ? tx.getBankAccount().getBank().name() : null,
            tx.getCreatedAt()
        );
    }

    @Transactional
    public ChargeResponseDTO createCharge(ChargeRequestDTO request) {
        BankIntegration integration = factory.getIntegration(request.bank());
        BankConfiguration config = bankConfigurationRepository.findById(request.bankConfigurationId())
                .orElseThrow(() -> new IllegalArgumentException("BankConfiguration não encontrada: " + request.bankConfigurationId()));

        log.info("[PaymentService] createCharge bank={} configId={} instrument={}",
                request.bank(), request.bankConfigurationId(), request.payment().instrument());

        BigDecimal amount = extractAmount(request);
        String payloadJson = serialize(request);

        String responseJson;
        try {
            responseJson = integration.processPayment(payloadJson, config);
        } catch (Exception e) {
            log.error("[PaymentService] Falha na integração bank={} configId={} instrument={}: {}",
                    request.bank(), request.bankConfigurationId(), request.payment().instrument(), e.getMessage(), e);
            throw e;
        }

        ChargeResponseDTO resp = parseChargeResponse(responseJson);

        Transaction tx = new Transaction();
        tx.setTenant(config.getTenant());
        tx.setBankAccount(config.getBankAccount());
        tx.setAmount(amount);
        tx.setType(TransactionType.CHARGE);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setDescription("Cobrança " + request.payment().instrument().name());

        if (resp.getId() != null) {
            tx.setExternalReference(resp.getId());
        }

        tx = transactionRepository.save(tx);

        TransactionLog txLog = new TransactionLog();
        txLog.setTenant(config.getTenant());
        txLog.setTransaction(tx);
        txLog.setEventType("CREATED");
        txLog.setMessage("Transação criada no provedor " + request.bank().name());
        txLog.setSeverity(Severity.INFO);

        Map<String, Object> md = new HashMap<>();
        md.put("provider", request.bank().name());
        md.put("instrument", request.payment().instrument().name());
        md.put("providerId", resp.getId());
        md.put("status", resp.getStatus());
        md.put("amount", resp.getAmount());
        md.put("locId", resp.getLocId());
        md.put("location", resp.getLocation());
        txLog.setMetadata(md);

        transactionLogRepository.save(txLog);

        return resp;
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
                return request.payment().boleto().items().stream()
                        .map(i -> new BigDecimal(i.valueInCents()).movePointLeft(2).multiply(new BigDecimal(i.amount())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            default:
                throw new IllegalArgumentException("Instrumento não suportado para cálculo de amount.");
        }
    }

    private ChargeResponseDTO parseChargeResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, ChargeResponseDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta do provedor em formato inválido.", e);
        }
    }
}
