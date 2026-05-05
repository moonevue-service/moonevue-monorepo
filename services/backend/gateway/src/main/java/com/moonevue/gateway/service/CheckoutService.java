package com.moonevue.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Transaction;
import com.moonevue.core.entity.TransactionLog;
import com.moonevue.core.enums.CheckoutAccessMode;
import com.moonevue.core.enums.Severity;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import com.moonevue.core.repository.TransactionLogRepository;
import com.moonevue.core.repository.TransactionRepository;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.dto.CheckoutClientLookupDTO;
import com.moonevue.gateway.dto.CheckoutInfoDTO;
import com.moonevue.gateway.dto.CheckoutPayRequest;
import com.moonevue.gateway.service.bank.BankIntegration;
import com.moonevue.gateway.util.ExtraConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final BankIntegrationFactory integrationFactory;
    private final ObjectMapper objectMapper;

    public CheckoutService(JdbcTemplate jdbcTemplate,
                           TransactionRepository transactionRepository,
                           TransactionLogRepository transactionLogRepository,
                           BankIntegrationFactory integrationFactory,
                           ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionRepository = transactionRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.integrationFactory = integrationFactory;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CheckoutInfoDTO getInfo(UUID token) {
        Transaction tx = findCheckoutByToken(token);
        markExpiredIfNeeded(tx);
        return toInfo(tx, null);
    }

    @Transactional(readOnly = true)
    public CheckoutInfoDTO getStatus(UUID token) {
        Transaction tx = findCheckoutByToken(token);
        markExpiredIfNeeded(tx);
        return toInfo(tx, null);
    }

    @Transactional(readOnly = true)
    public CheckoutClientLookupDTO lookupClient(UUID token, String document) {
        Transaction tx = findCheckoutByToken(token);
        markExpiredIfNeeded(tx);

        String normalizedDocument = document == null ? "" : document.replaceAll("[^0-9]", "");
        if (normalizedDocument.isBlank()) {
            return new CheckoutClientLookupDTO(false, null, null, null);
        }

        String lookupDocument = tx.getClient() != null && tx.getClient().getCpfCnpj() != null
            ? tx.getClient().getCpfCnpj()
            : normalizedDocument;

        List<CheckoutClientLookupDTO> results = jdbcTemplate.query(
            """
            SELECT name, email, phone
            FROM clients
            WHERE tenant_id = ?
              AND cpf_cnpj = ?
              AND status = 'ACTIVE'
            LIMIT 1
            """,
            (rs, rowNum) -> new CheckoutClientLookupDTO(
                true,
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone")
            ),
            tx.getTenant().getId(),
            lookupDocument
        );

        if (results.isEmpty()) {
            return new CheckoutClientLookupDTO(false, null, null, null);
        }

        return results.getFirst();
    }

    @Transactional
    public CheckoutInfoDTO identify(UUID token, String document) {
        Transaction tx = findCheckoutByToken(token);
        markExpiredIfNeeded(tx);

        CheckoutAccessMode accessMode = tx.getCheckoutAccessMode() != null
                ? tx.getCheckoutAccessMode()
                : CheckoutAccessMode.PUBLIC;

        if (accessMode == CheckoutAccessMode.PUBLIC) {
            tx.setCheckoutIdentityVerifiedAt(OffsetDateTime.now());
            transactionRepository.save(tx);
            return toInfo(tx, null);
        }

        if (tx.getClient() == null) {
            throw new IllegalStateException("Checkout sem cliente vinculado para validação de identidade");
        }

        String normalizedDocument = document == null ? "" : document.replaceAll("[^0-9]", "");
        String expectedDocument = tx.getClient().getCpfCnpj() == null
                ? ""
                : tx.getClient().getCpfCnpj().replaceAll("[^0-9]", "");

        if (normalizedDocument.isBlank() || !normalizedDocument.equals(expectedDocument)) {
            throw new IllegalArgumentException("Documento não confere com o cliente desta cobrança");
        }

        tx.setCheckoutIdentityVerifiedAt(OffsetDateTime.now());
        transactionRepository.save(tx);
        return toInfo(tx, null);
    }

    @Transactional
    public CheckoutInfoDTO pay(UUID token, CheckoutPayRequest req) {
        Transaction tx = findCheckoutForPayment(token);

        if (tx.getCheckoutInstrument() != null && req.instrument() != null && !tx.getCheckoutInstrument().equals(req.instrument())) {
            throw new IllegalArgumentException("Instrumento inválido para este checkout");
        }

        tx.setPayerName(req.payerName());
        tx.setPayerEmail(req.payerEmail());
        tx.setPayerDocument(req.payerDocument());
        tx.setPayerPhone(req.payerPhone());
        tx.setStatus(TransactionStatus.PROCESSING);

        BankConfiguration config = tx.getBankConfiguration();
        if (config == null) {
            throw new IllegalStateException("Transação sem configuração bancária para checkout");
        }

        String effectivePixKey = null;
        String instrument = tx.getCheckoutInstrument() != null ? tx.getCheckoutInstrument() : req.instrument();
        if ("PIX_IMMEDIATE".equals(instrument) || "PIX_DUE".equals(instrument)) {
            if (req.pixKey() != null && !req.pixKey().isBlank()) {
                effectivePixKey = req.pixKey();
            } else {
                effectivePixKey = resolveCompanyPixKey(tx, config);
            }
        }

        ChargeRequestDTO chargeRequest = buildChargeRequest(tx, req, config.getId(), effectivePixKey);

        String payloadJson;
        String responseJson;
        try {
            payloadJson = objectMapper.writeValueAsString(chargeRequest);
            tx.setProviderPayload(payloadJson);
            transactionRepository.save(tx);

            BankIntegration integration = integrationFactory.getIntegration(config.getBankAccount().getBank());
            responseJson = integration.processPayment(payloadJson, config);
        } catch (Exception e) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureReason(e.getMessage());
            transactionRepository.save(tx);
            log.error("[CheckoutService] Falha no pagamento token={}: {}", token, e.getMessage(), e);
            throw new RuntimeException("Falha ao processar pagamento: " + e.getMessage(), e);
        }

        ChargeResponseDTO resp;
        try {
            resp = objectMapper.readValue(responseJson, ChargeResponseDTO.class);
        } catch (Exception e) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureReason("Resposta inválida do provedor");
            transactionRepository.save(tx);
            throw new IllegalStateException("Resposta inválida do provedor", e);
        }

        tx.setProviderResponse(responseJson);
        tx.setExternalReference(resp.getId());
        tx.setType(TransactionType.CHARGE);

        TransactionStatus nextStatus = mapStatusFromProvider(resp.getStatus());
        tx.setStatus(nextStatus);
        tx.setPaidAt(nextStatus == TransactionStatus.PAID ? OffsetDateTime.now() : null);
        tx.setFailureReason(null);
        transactionRepository.save(tx);

        TransactionLog tlog = new TransactionLog();
        tlog.setTenant(tx.getTenant());
        tlog.setTransaction(tx);
        tlog.setEventType("CHECKOUT_PAYMENT");
        tlog.setMessage("Pagamento iniciado via checkout");
        tlog.setSeverity(Severity.INFO);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("token", token.toString());
        metadata.put("instrument", tx.getCheckoutInstrument());
        metadata.put("providerId", resp.getId());
        metadata.put("status", resp.getStatus());
        tlog.setMetadata(metadata);
        transactionLogRepository.save(tlog);

        return toInfo(tx, resp);
    }

    private Transaction findCheckoutByToken(UUID token) {
        Transaction tx = transactionRepository.findByCheckoutToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Link de checkout não encontrado"));

        return tx;
    }

    private void markExpiredIfNeeded(Transaction tx) {
        if (tx.getCheckoutExpiresAt() != null && tx.getCheckoutExpiresAt().isBefore(OffsetDateTime.now())) {
            if (tx.getStatus() == TransactionStatus.CHECKOUT_OPEN) {
                tx.setStatus(TransactionStatus.EXPIRED);
                transactionRepository.save(tx);
            }
        }
    }

    private Transaction findCheckoutForPayment(UUID token) {
        Transaction tx = findCheckoutByToken(token);
        markExpiredIfNeeded(tx);

        if (tx.getCheckoutExpiresAt() != null && tx.getCheckoutExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Este checkout expirou");
        }

        if (tx.getStatus() == TransactionStatus.CANCELED || tx.getStatus() == TransactionStatus.EXPIRED) {
            throw new IllegalStateException("Este checkout não está mais disponível");
        }

        if (tx.getStatus() == TransactionStatus.PROCESSING
            || tx.getStatus() == TransactionStatus.PENDING
            || tx.getStatus() == TransactionStatus.AUTHORIZED
            || tx.getStatus() == TransactionStatus.PAID
            || tx.getStatus() == TransactionStatus.SETTLED
            || tx.getStatus() == TransactionStatus.CAPTURED) {
            throw new IllegalStateException("Este checkout já foi pago");
        }

        CheckoutAccessMode accessMode = tx.getCheckoutAccessMode() != null
                ? tx.getCheckoutAccessMode()
                : CheckoutAccessMode.PUBLIC;

        if (accessMode != CheckoutAccessMode.PUBLIC && tx.getCheckoutIdentityVerifiedAt() == null) {
            throw new IllegalStateException("Valide sua identidade antes de prosseguir com o pagamento");
        }

        return tx;
    }

    private CheckoutInfoDTO toInfo(Transaction tx, ChargeResponseDTO result) {
        ChargeResponseDTO paymentResult = result;
        if (paymentResult == null && tx.getProviderResponse() != null) {
            try {
                paymentResult = objectMapper.readValue(tx.getProviderResponse(), ChargeResponseDTO.class);
            } catch (Exception ignored) {
            }
        }

        CheckoutAccessMode accessMode = tx.getCheckoutAccessMode() != null
                ? tx.getCheckoutAccessMode()
                : CheckoutAccessMode.PUBLIC;

        String maskedDocument = null;
        if (tx.getClient() != null && tx.getClient().getCpfCnpj() != null) {
            maskedDocument = maskDocument(tx.getClient().getCpfCnpj());
        }

        return new CheckoutInfoDTO(
                tx.getCheckoutToken(),
                tx.getAmount(),
                tx.getDescription(),
                List.of(tx.getCheckoutInstrument() != null ? tx.getCheckoutInstrument() : "PIX_IMMEDIATE"),
                tx.getStatus().name(),
                tx.getCheckoutExpiresAt(),
                accessMode.name(),
                tx.getClient() != null ? tx.getClient().getId() : null,
                tx.getClient() != null ? tx.getClient().getName() : null,
                maskedDocument,
                tx.getCheckoutIdentityVerifiedAt() != null,
                tx.getBankAccount().getBank().name(),
                paymentResult
        );
    }

    private String maskDocument(String document) {
        String normalized = document.replaceAll("[^0-9]", "");
        if (normalized.length() <= 4) {
            return "****";
        }
        return "***" + normalized.substring(normalized.length() - 4);
    }

    private ChargeRequestDTO buildChargeRequest(Transaction tx, CheckoutPayRequest req, Long configId, String effectivePixKey) {
        String instrument = tx.getCheckoutInstrument() != null ? tx.getCheckoutInstrument() : req.instrument();
        BigDecimal amount = tx.getAmount();

        String rawDoc = req.payerDocument() != null ? req.payerDocument().replaceAll("[^0-9]", "") : "";
        String cpf = rawDoc.length() == 11 ? rawDoc : null;
        String cnpj = rawDoc.length() == 14 ? rawDoc : null;

        ChargeRequestDTO.Payment payment = switch (instrument) {
            case "PIX_IMMEDIATE" -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.PIX_IMMEDIATE,
                    new ChargeRequestDTO.PixImmediate(
                            3600,
                            cpf, cnpj,
                            req.payerName(),
                            amount,
                            tx.getDescription(),
                                effectivePixKey
                    ),
                    null,
                    null
            );
            case "PIX_DUE" -> {
                LocalDate dueDate = OffsetDateTime.now().plusDays(1).toLocalDate();
                yield new ChargeRequestDTO.Payment(
                        ChargeRequestDTO.Instrument.PIX_DUE,
                        null,
                        new ChargeRequestDTO.PixDue(
                                UUID.randomUUID().toString().replaceAll("-", "").substring(0, 26),
                                dueDate,
                                30,
                                cpf,
                                cnpj,
                                req.payerName(),
                                null,
                                null,
                                null,
                                null,
                                amount,
                                null,
                                null,
                                null,
                                null,
                                tx.getDescription(),
                                effectivePixKey
                        ),
                        null
                );
            }
            case "BOLETO" -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.BOLETO,
                    null,
                    null,
                    new ChargeRequestDTO.Boleto(
                            List.of(new ChargeRequestDTO.Boleto.Item(
                                    tx.getDescription(),
                                    amount.multiply(new BigDecimal("100")).intValue(),
                                    1
                            )),
                            new ChargeRequestDTO.Boleto.Customer(
                                    req.payerName(),
                                    cpf,
                                    req.payerEmail(),
                                    req.payerPhone(),
                                    cnpj != null ? new ChargeRequestDTO.Boleto.Customer.Juridical(req.payerName(), cnpj) : null,
                                    null
                            ),
                            OffsetDateTime.now().plusDays(3).toLocalDate(),
                            null,
                            tx.getDescription()
                    )
            );
            default -> throw new IllegalArgumentException("Instrumento não suportado: " + instrument);
        };

        return new ChargeRequestDTO(
                tx.getBankAccount().getBank(),
                configId,
                payment
        );
    }

    private String resolveCompanyPixKey(Transaction tx, BankConfiguration config) {
        if (tx.getCheckoutPixKey() != null && !tx.getCheckoutPixKey().isBlank()) {
            return tx.getCheckoutPixKey();
        }

        Map<String, Object> extra = config.getExtraConfig();
        String fromConfig = ExtraConfigUtils.getString(extra, "pix.pixKey", null);
        if (fromConfig == null || fromConfig.isBlank()) {
            fromConfig = ExtraConfigUtils.getString(extra, "pix.chave", null);
        }
        if (fromConfig == null || fromConfig.isBlank()) {
            fromConfig = ExtraConfigUtils.getString(extra, "pix.key", null);
        }

        if (fromConfig == null || fromConfig.isBlank()) {
            throw new IllegalArgumentException("A chave PIX da empresa não está configurada. Configure em Conta Bancária > Integração EFI antes de cobrar via PIX.");
        }

        tx.setCheckoutPixKey(fromConfig);
        return fromConfig;
    }

    private TransactionStatus mapStatusFromProvider(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return TransactionStatus.PROCESSING;
        }

        String normalized = providerStatus.trim().toUpperCase();

        if (normalized.contains("PAGO")
                || normalized.contains("PAID")
                || normalized.contains("SETTLED")
                || normalized.contains("CONCLUID")) {
            return TransactionStatus.PAID;
        }

        if (normalized.contains("CANCEL") || normalized.contains("REJECT") || normalized.contains("NEGAD")) {
            return TransactionStatus.FAILED;
        }

        return TransactionStatus.PROCESSING;
    }
}
