package com.moonevue.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Charge;
import com.moonevue.core.entity.Client;
import com.moonevue.core.entity.Transaction;
import com.moonevue.core.entity.TransactionLog;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.enums.CheckoutAccessMode;
import com.moonevue.core.enums.Severity;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import com.moonevue.core.repository.BankConfigurationRepository;
import com.moonevue.core.repository.ChargeRepository;
import com.moonevue.core.repository.ClientRepository;
import com.moonevue.core.repository.TransactionLogRepository;
import com.moonevue.core.repository.TransactionRepository;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.dto.ChargeSummaryDTO;
import com.moonevue.gateway.dto.CreateCheckoutTransactionRequest;
import com.moonevue.gateway.dto.TransactionSummaryDTO;
import com.moonevue.gateway.service.bank.ASAAS.AsaasCustomerService;
import com.moonevue.gateway.service.bank.BankIntegration;
import com.moonevue.gateway.service.policy.DebtorRequirementPolicy;
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
import java.util.List;
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
    private final ChargeRepository chargeRepository;
    private final DebtorRequirementPolicy debtorRequirementPolicy;
    private final ClientBankIntegrationService clientBankIntegrationService;
    private final AsaasCustomerService asaasCustomerService;

    public PaymentService(BankIntegrationFactory factory,
                          ObjectMapper objectMapper,
                          BankConfigurationRepository bankConfigurationRepository,
                          ClientRepository clientRepository,
                          TransactionRepository transactionRepository,
                          TransactionLogRepository transactionLogRepository,
                          ChargeRepository chargeRepository,
                          DebtorRequirementPolicy debtorRequirementPolicy,
                          ClientBankIntegrationService clientBankIntegrationService,
                          AsaasCustomerService asaasCustomerService) {
        this.factory = factory;
        this.objectMapper = objectMapper;
        this.bankConfigurationRepository = bankConfigurationRepository;
        this.clientRepository = clientRepository;
        this.transactionRepository = transactionRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.chargeRepository = chargeRepository;
        this.debtorRequirementPolicy = debtorRequirementPolicy;
        this.clientBankIntegrationService = clientBankIntegrationService;
        this.asaasCustomerService = asaasCustomerService;
    }

    @Transactional(readOnly = true)
    public Page<TransactionSummaryDTO> listTransactions(Long tenantId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return transactionRepository.findByTenantId(tenantId, pageable)
            .map(t -> {
                String[] boletoLinks = extractBoletoLinksFromCharge(t.getId(), tenantId, t.getCheckoutInstrument());
                return new TransactionSummaryDTO(
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
                    t.getCreatedAt(),
                    boletoLinks[0],
                    boletoLinks[1],
                    t.getBankConfiguration() != null && t.getBankConfiguration().getEnvironment() != null
                        ? t.getBankConfiguration().getEnvironment().name() : null
                );
            });
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
            : (request.clientId() != null ? CheckoutAccessMode.CLIENT_LOGIN : CheckoutAccessMode.PUBLIC);
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
            tx.getCreatedAt(),
            null,
            null,
            config.getEnvironment() != null ? config.getEnvironment().name() : null
        );
    }

        @Transactional(readOnly = true)
        public List<ChargeSummaryDTO> listChargesForTransaction(Long tenantId, Long transactionId) {
        transactionRepository.findByIdAndTenantId(transactionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));

        return chargeRepository.findByTransactionIdAndTenantIdOrderByCreatedAtDesc(transactionId, tenantId)
            .stream()
            .map(c -> new ChargeSummaryDTO(
                c.getId(),
                c.getProvider(),
                c.getProviderChargeId(),
                c.getPaymentMethod(),
                c.getStatus(),
                c.getAmountTotal(),
                c.getAmountPaid(),
                c.getPixCopyPaste(),
                c.getBoletoLine(),
                c.getPixQrCodeRef(),
                c.getBoletoPdfRef(),
                c.getCreatedAt()
            ))
            .toList();
        }

    @Transactional
    public ChargeResponseDTO createCharge(Long tenantId, ChargeRequestDTO request, Long clientId) {
        return createCharge(tenantId, request, clientId, null);
    }

    @Transactional
    public ChargeResponseDTO createCharge(Long tenantId, ChargeRequestDTO request, Long clientId, Long apiKeyId) {
        Long bankConfigurationId = request.bankConfigurationId();
        BankIntegration integration = factory.getIntegration(request.bank());
        BankConfiguration config = bankConfigurationRepository.findById(bankConfigurationId)
            .orElseThrow(() -> new IllegalArgumentException("BankConfiguration não encontrada: " + bankConfigurationId));

        if (!config.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("BankConfiguration não pertence ao tenant");
        }

        log.info("[PaymentService] createCharge bank={} configId={} instrument={}",
                request.bank(), request.bankConfigurationId(), request.payment().instrument());

        Client client = null;
        if (clientId != null) {
            client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado: " + clientId));
            if (!client.getTenant().getId().equals(tenantId)) {
                throw new IllegalArgumentException("Cliente não pertence ao tenant");
            }
        }

        // Valida devedor/pagador conforme a política do provedor + tipo de cobrança.
        debtorRequirementPolicy.validate(request.bank(), request);

        // Garante o vínculo de integração bancária do cliente (ex.: identificador interno EFI).
        if (request.bank() == BankType.ASAAS) {
            if (client != null) {
                String customerId = resolveAsaasCustomerId(config, client);
                request = withAsaasCustomerHint(request, customerId);
            } else {
                String customerId = resolveAsaasCustomerIdFromRequest(config, request);
                request = withAsaasCustomerHint(request, customerId);
            }
        } else if (client != null) {
            clientBankIntegrationService.ensureIntegration(client, request.bank().name());
        }

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

        Transaction tx = new Transaction();
        tx.setTenant(config.getTenant());
        tx.setBankAccount(config.getBankAccount());
        tx.setBankConfiguration(config);
        tx.setClient(client);
        tx.setApiKeyId(apiKeyId);
        tx.setSourceChannel(apiKeyId != null ? "PUBLIC_API" : "INTERNAL_PANEL");
        tx.setAmount(amount);
        tx.setType(TransactionType.CHARGE);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setDescription("Cobrança " + request.payment().instrument().name());
        tx.setCheckoutInstrument(request.payment().instrument().name());
        tx.setCheckoutAccessMode(CheckoutAccessMode.PUBLIC);
        tx.setCheckoutToken(UUID.randomUUID());
        tx.setCheckoutExpiresAt(OffsetDateTime.now().plusHours(24));
        if (client != null) {
            tx.setPayerName(client.getName());
            tx.setPayerDocument(client.getCpfCnpj());
            tx.setPayerEmail(client.getEmail());
            tx.setPayerPhone(client.getPhone());
        }

        tx = transactionRepository.save(tx);

        return processAndPersistCharge(tx, request, config, amount, payloadJson, responseJson, "CREATED", "Transação criada no provedor ");
    }

    @Transactional
    public ChargeResponseDTO emitChargeForTransaction(Long tenantId,
                                                      Long transactionId,
                                                      String instrument,
                                                      String pixKey) {
        Transaction tx = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));

        if (tx.getStatus() == TransactionStatus.PAID
                || tx.getStatus() == TransactionStatus.CAPTURED
                || tx.getStatus() == TransactionStatus.SETTLED) {
            throw new IllegalArgumentException("Transação já está paga e não pode ser reemitida");
        }

        BankConfiguration config = tx.getBankConfiguration();
        if (config == null) {
            throw new IllegalArgumentException("Transação sem bankConfiguration associada");
        }

        String effectiveInstrument = instrument != null && !instrument.isBlank()
                ? instrument
                : tx.getCheckoutInstrument();

        if (effectiveInstrument == null || effectiveInstrument.isBlank()) {
            effectiveInstrument = "PIX_IMMEDIATE";
        }

        ChargeRequestDTO request = buildRequestFromTransaction(tx, config.getId(), effectiveInstrument, pixKey);

        BankIntegration integration = factory.getIntegration(request.bank());
        BigDecimal amount = extractAmount(request);
        String payloadJson = serialize(request);

        String responseJson;
        try {
            responseJson = integration.processPayment(payloadJson, config);
        } catch (Exception e) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureReason(e.getMessage());
            transactionRepository.save(tx);
            throw e;
        }

        return processAndPersistCharge(tx, request, config, amount, payloadJson, responseJson,
                "EMIT_CHARGE", "Cobrança emitida para transação ");
    }

    @Transactional
    public ChargeResponseDTO retryLastChargeForTransaction(Long tenantId,
                                                           Long transactionId,
                                                           String pixKey) {
        Transaction tx = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));

        Charge lastCharge = chargeRepository.findFirstByTransactionIdAndTenantIdOrderByCreatedAtDesc(transactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma cobrança encontrada para retry"));

        if (!List.of("FAILED", "EXPIRED", "CANCELED").contains(lastCharge.getStatus())) {
            throw new IllegalArgumentException("Retry permitido apenas para cobranças com falha/expirada/cancelada");
        }

        String instrument = mapInstrumentFromPaymentMethod(lastCharge.getPaymentMethod());
        return emitChargeForTransaction(tenantId, tx.getId(), instrument, pixKey);
    }

    private ChargeResponseDTO processAndPersistCharge(Transaction tx,
                                                      ChargeRequestDTO request,
                                                      BankConfiguration config,
                                                      BigDecimal amount,
                                                      String payloadJson,
                                                      String responseJson,
                                                      String eventType,
                                                      String messagePrefix) {
        ChargeResponseDTO resp = parseChargeResponse(responseJson);

        if (resp.getId() != null) {
            tx.setExternalReference(resp.getId());
        }
        tx.setProviderPayload(payloadJson);
        tx.setProviderResponse(responseJson);
        tx.setStatus(mapTransactionStatus(resp.getStatus()));
        tx.setFailureReason(null);
        transactionRepository.save(tx);

        Charge charge = new Charge();
        charge.setTenant(config.getTenant());
        charge.setTransaction(tx);
        charge.setProvider(request.bank().name());
        charge.setProviderChargeId(resp.getId());
        charge.setProviderTxid(resp.getId());
        charge.setPaymentMethod(mapPaymentMethod(request.payment().instrument()));
        charge.setStatus(mapChargeStatus(resp.getStatus()));
        charge.setAmountTotal(amount);
        charge.setAmountPaid(BigDecimal.ZERO);
        charge.setDueDate(parseDueDate(resp.getDueDate()));
        charge.setPixCopyPaste(resp.getPixCopiaECola());
        charge.setPixQrCodeRef(resp.getLocation());
        charge.setBoletoLine(resp.getBarcode());
        charge.setBoletoPdfRef(resp.getPdfLink());
        charge.setProviderPayload(payloadJson);
        charge.setProviderResponse(responseJson);
        chargeRepository.save(charge);

        TransactionLog txLog = new TransactionLog();
        txLog.setTenant(config.getTenant());
        txLog.setTransaction(tx);
        txLog.setEventType(eventType);
        txLog.setMessage(messagePrefix + request.bank().name());
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

    private TransactionStatus mapTransactionStatus(String providerStatus) {
        if (providerStatus == null) {
            return TransactionStatus.PENDING;
        }
        String normalized = providerStatus.trim().toUpperCase();
        if (List.of("CONCLUIDA", "PAID", "SETTLED", "CONFIRMED").contains(normalized)) {
            return TransactionStatus.PAID;
        }
        if (List.of("PROCESSING", "PENDING", "ACTIVE", "WAITING").contains(normalized)) {
            return TransactionStatus.PENDING;
        }
        if (List.of("EXPIRED", "CANCELED", "CANCELLED").contains(normalized)) {
            return TransactionStatus.CANCELED;
        }
        return TransactionStatus.PENDING;
    }

    private String mapChargeStatus(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return "CREATED";
        }
        String normalized = providerStatus.trim().toUpperCase();
        if (List.of("CONCLUIDA", "PAID", "SETTLED", "CONFIRMED").contains(normalized)) {
            return "PAID";
        }
        if (List.of("PROCESSING", "PENDING", "ACTIVE", "WAITING").contains(normalized)) {
            return "AWAITING_PAYMENT";
        }
        if (List.of("EXPIRED").contains(normalized)) {
            return "EXPIRED";
        }
        if (List.of("CANCELED", "CANCELLED", "FAILED", "ERROR").contains(normalized)) {
            return "FAILED";
        }
        return "CREATED";
    }

    private String mapPaymentMethod(ChargeRequestDTO.Instrument instrument) {
        return switch (instrument) {
            case PIX_IMMEDIATE, PIX_DUE -> "PIX";
            case BOLETO -> "BOLETO";
        };
    }

    private String mapInstrumentFromPaymentMethod(String paymentMethod) {
        if (paymentMethod == null) {
            return "PIX_IMMEDIATE";
        }
        return switch (paymentMethod.toUpperCase()) {
            case "BOLETO" -> "BOLETO";
            case "PIX" -> "PIX_IMMEDIATE";
            default -> "PIX_IMMEDIATE";
        };
    }

    private java.time.LocalDate parseDueDate(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(dueDate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ChargeRequestDTO buildRequestFromTransaction(Transaction tx,
                                                         Long bankConfigurationId,
                                                         String instrument,
                                                         String pixKey) {
        String payerName = tx.getPayerName() != null ? tx.getPayerName() : (tx.getClient() != null ? tx.getClient().getName() : null);
        String rawDocument = tx.getPayerDocument() != null ? tx.getPayerDocument() : (tx.getClient() != null ? tx.getClient().getCpfCnpj() : null);

        if (payerName == null || rawDocument == null) {
            throw new IllegalArgumentException("Transação precisa de pagador identificado para emitir cobrança");
        }

        String document = rawDocument.replaceAll("[^0-9]", "");
        String cpf = document.length() == 11 ? document : null;
        String cnpj = document.length() == 14 ? document : null;
        if (cpf == null && cnpj == null) {
            throw new IllegalArgumentException("Documento do pagador inválido para emissão");
        }

        ChargeRequestDTO.Payment payment = switch (instrument) {
            case "PIX_DUE" -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.PIX_DUE,
                    null,
                    new ChargeRequestDTO.PixDue(
                            UUID.randomUUID().toString().replaceAll("-", "").substring(0, 26),
                            tx.getDueDate() != null ? tx.getDueDate() : OffsetDateTime.now().plusDays(1).toLocalDate(),
                            30,
                            cpf,
                            cnpj,
                            payerName,
                            null,
                            null,
                            null,
                            null,
                            tx.getAmount(),
                            null,
                            null,
                            null,
                            null,
                            tx.getDescription(),
                            pixKey
                    ),
                    null
            );
            case "BOLETO" -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.BOLETO,
                    null,
                    null,
                    new ChargeRequestDTO.Boleto(
                            List.of(new ChargeRequestDTO.Boleto.Item(
                                    tx.getDescription() != null ? tx.getDescription() : "Cobrança",
                                    tx.getAmount().movePointRight(2).intValue(),
                                    1
                            )),
                            new ChargeRequestDTO.Boleto.Customer(
                                    payerName,
                                    cpf,
                                    tx.getPayerEmail(),
                                    tx.getPayerPhone(),
                                    cnpj != null ? new ChargeRequestDTO.Boleto.Customer.Juridical(payerName, cnpj) : null,
                                    null
                            ),
                            tx.getDueDate() != null ? tx.getDueDate() : OffsetDateTime.now().plusDays(3).toLocalDate(),
                            null,
                            tx.getDescription()
                    )
            );
            default -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.PIX_IMMEDIATE,
                    new ChargeRequestDTO.PixImmediate(
                            3600,
                            cpf,
                            cnpj,
                            payerName,
                            tx.getAmount(),
                            tx.getDescription(),
                            pixKey
                    ),
                    null,
                    null
            );
        };

        return new ChargeRequestDTO(tx.getBankAccount().getBank(), bankConfigurationId, payment);
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

    // ─── ASAAS customer helpers ──────────────────────────────────────────────

    private String resolveAsaasCustomerId(BankConfiguration config, Client client) {
        return clientBankIntegrationService.findExternalCustomerId(client.getId(), BankType.ASAAS.name(), config.getEnvironment())
                .orElseGet(() -> {
                    String existing = asaasCustomerService.findCustomerIdByDocument(config, client.getCpfCnpj());
                    if (existing != null && !existing.isBlank()) {
                        clientBankIntegrationService.ensureIntegration(client, BankType.ASAAS.name(), existing, config.getEnvironment());
                        return existing;
                    }
                    String created = asaasCustomerService.createCustomer(config, client);
                    clientBankIntegrationService.ensureIntegration(client, BankType.ASAAS.name(), created, config.getEnvironment());
                    return created;
                });
    }

    private String resolveAsaasCustomerIdFromRequest(BankConfiguration config, ChargeRequestDTO request) {
        String document = extractDocumentFromRequest(request);
        String name = extractNameFromRequest(request);
        String email = extractEmailFromRequest(request);
        String phone = extractPhoneFromRequest(request);

        if (document == null || (document.length() != 11 && document.length() != 14)) {
            throw new IllegalArgumentException("Para cobrança ASAAS sem cliente vinculado, informe CPF/CNPJ válido do pagador");
        }

        String existing = asaasCustomerService.findCustomerIdByDocument(config, document);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }

        Client tempClient = new Client();
        tempClient.setName(name != null && !name.isBlank() ? name : "Cliente Checkout");
        tempClient.setCpfCnpj(document);
        tempClient.setEmail(email);
        tempClient.setPhone(phone);
        return asaasCustomerService.createCustomer(config, tempClient);
    }

    private String extractDocumentFromRequest(ChargeRequestDTO request) {
        if (request == null || request.payment() == null) return null;
        return switch (request.payment().instrument()) {
            case PIX_IMMEDIATE -> {
                var p = request.payment().pixImmediate();
                if (p == null) yield null;
                String cpf = p.cpf() == null ? null : p.cpf().replaceAll("[^0-9]", "");
                String cnpj = p.cnpj() == null ? null : p.cnpj().replaceAll("[^0-9]", "");
                yield cpf != null && !cpf.isBlank() ? cpf : cnpj;
            }
            case PIX_DUE -> {
                var p = request.payment().pixDue();
                if (p == null) yield null;
                String cpf = p.cpf() == null ? null : p.cpf().replaceAll("[^0-9]", "");
                String cnpj = p.cnpj() == null ? null : p.cnpj().replaceAll("[^0-9]", "");
                yield cpf != null && !cpf.isBlank() ? cpf : cnpj;
            }
            case BOLETO -> {
                var b = request.payment().boleto();
                if (b == null || b.customer() == null) yield null;
                String cpf = b.customer().cpf() == null ? null : b.customer().cpf().replaceAll("[^0-9]", "");
                String cnpj = (b.customer().juridicalPerson() != null && b.customer().juridicalPerson().cnpj() != null)
                        ? b.customer().juridicalPerson().cnpj().replaceAll("[^0-9]", "") : null;
                yield cpf != null && !cpf.isBlank() ? cpf : cnpj;
            }
        };
    }

    private String extractNameFromRequest(ChargeRequestDTO request) {
        if (request == null || request.payment() == null) return null;
        return switch (request.payment().instrument()) {
            case PIX_IMMEDIATE -> request.payment().pixImmediate() != null ? request.payment().pixImmediate().nome() : null;
            case PIX_DUE -> request.payment().pixDue() != null ? request.payment().pixDue().nome() : null;
            case BOLETO -> request.payment().boleto() != null && request.payment().boleto().customer() != null
                    ? request.payment().boleto().customer().name() : null;
        };
    }

    private String extractEmailFromRequest(ChargeRequestDTO request) {
        if (request == null || request.payment() == null || request.payment().instrument() != ChargeRequestDTO.Instrument.BOLETO) return null;
        if (request.payment().boleto() == null || request.payment().boleto().customer() == null) return null;
        return request.payment().boleto().customer().email();
    }

    private String extractPhoneFromRequest(ChargeRequestDTO request) {
        if (request == null || request.payment() == null || request.payment().instrument() != ChargeRequestDTO.Instrument.BOLETO) return null;
        if (request.payment().boleto() == null || request.payment().boleto().customer() == null) return null;
        return request.payment().boleto().customer().phoneNumber();
    }

    private ChargeRequestDTO withAsaasCustomerHint(ChargeRequestDTO request, String customerId) {
        if (customerId == null || customerId.isBlank() || request == null || request.payment() == null) return request;
        ChargeRequestDTO.Payment payment = request.payment();
        ChargeRequestDTO.Payment patched = switch (payment.instrument()) {
            case PIX_IMMEDIATE -> {
                var p = payment.pixImmediate();
                if (p == null) yield payment;
                yield new ChargeRequestDTO.Payment(payment.instrument(),
                        new ChargeRequestDTO.PixImmediate(p.expiracaoSeconds(), p.cpf(), p.cnpj(), p.nome(), p.amount(), p.solicitacaoPagador(), customerId),
                        payment.pixDue(), payment.boleto());
            }
            case PIX_DUE -> {
                var p = payment.pixDue();
                if (p == null) yield payment;
                yield new ChargeRequestDTO.Payment(payment.instrument(), payment.pixImmediate(),
                        new ChargeRequestDTO.PixDue(p.txid(), p.dataDeVencimento(), p.validadeAposVencimento(),
                                p.cpf(), p.cnpj(), p.nome(), p.logradouro(), p.cidade(), p.uf(), p.cep(),
                                p.amountOriginal(), p.multaPerc(), p.jurosPerc(), p.descontoData(), p.descontoValorPerc(),
                                p.solicitacaoPagador(), customerId),
                        payment.boleto());
            }
            case BOLETO -> {
                var b = payment.boleto();
                if (b == null) yield payment;
                var c = b.customer();
                var patchedCustomer = new ChargeRequestDTO.Boleto.Customer(
                        c != null ? c.name() : null, customerId,
                        c != null ? c.email() : null, c != null ? c.phoneNumber() : null,
                        c != null ? c.juridicalPerson() : null, c != null ? c.address() : null);
                yield new ChargeRequestDTO.Payment(payment.instrument(), payment.pixImmediate(), payment.pixDue(),
                        new ChargeRequestDTO.Boleto(b.items(), patchedCustomer, b.expireAt(), b.configurations(), b.message()));
            }
        };
        return new ChargeRequestDTO(request.bank(), request.bankConfigurationId(), patched);
    }

    // ─── Boleto link helper ──────────────────────────────────────────────────

    private String[] extractBoletoLinksFromCharge(Long transactionId, Long tenantId, String instrument) {
        if (instrument == null) return new String[]{null, null};
        String inst = instrument.toUpperCase();
        if (!inst.contains("BOLETO")) return new String[]{null, null};
        return chargeRepository.findFirstByTransactionIdAndTenantIdOrderByCreatedAtDesc(transactionId, tenantId)
                .map(c -> new String[]{c.getPixQrCodeRef(), c.getBoletoPdfRef()})
                .orElse(new String[]{null, null});
    }
}
