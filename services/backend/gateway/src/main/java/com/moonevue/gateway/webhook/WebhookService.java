package com.moonevue.gateway.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.Charge;
import com.moonevue.core.entity.Transaction;
import com.moonevue.core.entity.TransactionLog;
import com.moonevue.core.entity.WebhookEvent;
import com.moonevue.core.repository.ChargeRepository;
import com.moonevue.core.enums.Severity;
import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.repository.TransactionLogRepository;
import com.moonevue.core.repository.TransactionRepository;
import com.moonevue.core.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final TransactionRepository transactionRepository;
    private final ChargeRepository chargeRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String provider, String payload, String idemKey) {
        String normalizedProvider = provider == null ? "unknown" : provider.trim().toLowerCase();
        String eventKey = buildEventKey(normalizedProvider, payload, idemKey);
        log.info("[Webhook] Evento recebido provider={} eventKey={}", normalizedProvider, eventKey);

        WebhookEvent event = webhookEventRepository.findByProviderAndEventKey(normalizedProvider, eventKey)
                .orElseGet(() -> {
                    WebhookEvent created = new WebhookEvent();
                    created.setProvider(normalizedProvider);
                    created.setEventKey(eventKey);
                    created.setPayload(payload == null ? "" : payload);
                    return created;
                });

        if (Boolean.TRUE.equals(event.getProcessed())) {
            log.info("webhook duplicado provider={} eventKey={}", normalizedProvider, eventKey);
            return;
        }

        // Atualiza o payload na tentativa corrente para auditoria e reprocessamento.
        event.setPayload(payload == null ? "" : payload);
        webhookEventRepository.save(event);

        try {
            if ("efi".equals(normalizedProvider)) {
                int updated = processEfiWebhook(payload, eventKey);
                event.setProcessed(true);
                event.setResult("PROCESSED");
                event.setMessage("Transações atualizadas=" + updated);
                event.setProcessedAt(OffsetDateTime.now());
                webhookEventRepository.save(event);
                log.info("[Webhook] Processamento concluído provider={} eventKey={} atualizacoes={}", normalizedProvider, eventKey, updated);
                return;
            }

            if ("asaas".equals(normalizedProvider)) {
                int updated = processAsaasWebhook(payload, eventKey);
                event.setProcessed(true);
                event.setResult("PROCESSED");
                event.setMessage("Transações atualizadas=" + updated);
                event.setProcessedAt(OffsetDateTime.now());
                webhookEventRepository.save(event);
                log.info("[Webhook] Processamento concluído provider={} eventKey={} atualizacoes={}", normalizedProvider, eventKey, updated);
                return;
            }

                event.setProcessed(true);
                event.setResult("IGNORED");
                event.setMessage("Provider sem processador específico");
                event.setProcessedAt(OffsetDateTime.now());
                webhookEventRepository.save(event);
                log.info("[Webhook] Evento ignorado provider={} eventKey={}", normalizedProvider, eventKey);
        } catch (Exception ex) {
            event.setProcessed(false);
            event.setResult("ERROR");
            event.setMessage(ex.getMessage());
            event.setProcessedAt(OffsetDateTime.now());
            webhookEventRepository.save(event);
            throw new RuntimeException("Falha ao processar webhook", ex);
        }
    }

    private String buildEventKey(String provider, String payload, String idemKey) {
        if (idemKey != null && !idemKey.isBlank()) {
            return idemKey.trim();
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((provider + ":" + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return provider + ":" + Math.abs((payload == null ? "" : payload).hashCode());
        }
    }

    private int processEfiWebhook(String payload, String eventKey) throws Exception {
        JsonNode root = objectMapper.readTree(payload == null ? "{}" : payload);
        List<EfiPixEvent> pixEvents = extractEfiPixEvents(root);

        int updated = 0;
        for (EfiPixEvent pix : pixEvents) {
            if (pix.txid() == null || pix.txid().isBlank()) {
                continue;
            }

            Optional<Transaction> txOpt = transactionRepository.findFirstByExternalReferenceOrderByIdDesc(pix.txid());
            if (txOpt.isEmpty()) {
                log.info("Webhook EFI sem transação para txid={}", pix.txid());
                continue;
            }

            Transaction tx = txOpt.get();
            boolean alreadyPaid = tx.getStatus() == TransactionStatus.PAID
                    || tx.getStatus() == TransactionStatus.SETTLED
                    || tx.getStatus() == TransactionStatus.CAPTURED;

            if (!alreadyPaid) {
                tx.setStatus(TransactionStatus.PAID);
                tx.setPaidAt(OffsetDateTime.now());
                tx.setFailureReason(null);
                tx.setProviderResponse(payload);
                transactionRepository.save(tx);
                updated++;
            }

            TransactionLog tlog = new TransactionLog();
            tlog.setTenant(tx.getTenant());
            tlog.setTransaction(tx);
            tlog.setEventType("WEBHOOK_EFI_PIX");
            tlog.setSeverity(Severity.INFO);
            tlog.setMessage(alreadyPaid ? "Webhook recebido para transação já paga" : "Pagamento confirmado via webhook EFI");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("eventKey", eventKey);
            metadata.put("txid", pix.txid());
            metadata.put("endToEndId", pix.endToEndId());
            metadata.put("status", pix.status());
            metadata.put("amount", pix.amount());
            tlog.setMetadata(metadata);
            transactionLogRepository.save(tlog);
        }

        return updated;
    }

    private int processAsaasWebhook(String payload, String eventKey) throws Exception {
        JsonNode root = objectMapper.readTree(payload == null ? "{}" : payload);
        String eventType = text(root, "event");
        JsonNode paymentNode = root.path("payment");
        if (paymentNode.isMissingNode() || paymentNode.isNull()) {
            paymentNode = root;
        }

        String paymentId = text(paymentNode, "id");
        if (paymentId == null) {
            log.info("Webhook ASAAS sem payment.id eventKey={}", eventKey);
            return 0;
        }

        Optional<Charge> chargeOpt = chargeRepository.findFirstByProviderAndProviderChargeIdOrderByCreatedAtDesc("ASAAS", paymentId);
        if (chargeOpt.isEmpty()) {
            log.info("Webhook ASAAS sem charge para paymentId={} eventKey={}", paymentId, eventKey);
            return 0;
        }

        Charge charge = chargeOpt.get();
        Transaction tx = charge.getTransaction();

        String statusFromPayload = text(paymentNode, "status");
        String normalizedStatus = normalizeAsaasStatus(eventType, statusFromPayload);
        String mappedChargeStatus = mapAsaasChargeStatus(normalizedStatus);
        TransactionStatus mappedTxStatus = mapAsaasTransactionStatus(normalizedStatus);

        boolean changed = false;
        if (mappedChargeStatus != null && !mappedChargeStatus.equalsIgnoreCase(charge.getStatus())) {
            charge.setStatus(mappedChargeStatus);
            changed = true;
        }

        if (isPaidStatus(normalizedStatus)) {
            if (charge.getPaidAt() == null) {
                charge.setPaidAt(OffsetDateTime.now());
                changed = true;
            }
            if (tx.getPaidAt() == null) {
                tx.setPaidAt(OffsetDateTime.now());
                changed = true;
            }
        }

        if (mappedTxStatus != null && tx.getStatus() != mappedTxStatus) {
            tx.setStatus(mappedTxStatus);
            if (mappedTxStatus != TransactionStatus.FAILED) {
                tx.setFailureReason(null);
            }
            changed = true;
        }

        tx.setProviderResponse(payload);
        charge.setProviderResponse(payload);

        if (changed) {
            chargeRepository.save(charge);
            transactionRepository.save(tx);
        }

        TransactionLog tlog = new TransactionLog();
        tlog.setTenant(tx.getTenant());
        tlog.setTransaction(tx);
        tlog.setEventType("WEBHOOK_ASAAS_" + (eventType == null ? "UNKNOWN" : eventType.toUpperCase(Locale.ROOT)));
        tlog.setSeverity(Severity.INFO);
        tlog.setMessage(changed
                ? "Status atualizado por webhook ASAAS"
                : "Webhook ASAAS recebido sem alteração de status");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventKey", eventKey);
        metadata.put("event", eventType);
        metadata.put("paymentId", paymentId);
        metadata.put("providerStatus", statusFromPayload);
        metadata.put("mappedStatus", normalizedStatus);
        metadata.put("transactionStatus", tx.getStatus().name());
        metadata.put("chargeStatus", charge.getStatus());
        tlog.setMetadata(metadata);
        transactionLogRepository.save(tlog);

        return changed ? 1 : 0;
    }

    private String normalizeAsaasStatus(String eventType, String statusFromPayload) {
        // O eventType tem precedência sobre payment.status: o ASAAS pode enviar
        // um evento PAYMENT_RECEIVED com payment.status=PENDING (status anterior),
        // mas o evento define o que realmente ocorreu.
        if (eventType != null && !eventType.isBlank()) {
            String mapped = switch (eventType.trim().toUpperCase(Locale.ROOT)) {
                case "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_OVERDUE_RECEIVED" -> "RECEIVED";
                case "PAYMENT_OVERDUE" -> "OVERDUE";
                case "PAYMENT_DELETED", "PAYMENT_REFUNDED", "PAYMENT_REFUND_IN_PROGRESS" -> "REFUNDED";
                case "PAYMENT_DUNNING_RECEIVED" -> "RECEIVED";
                default -> null;
            };
            if (mapped != null) {
                return mapped;
            }
        }
        // Fallback: usa o status do payload quando não houver mapeamento pelo eventType
        if (statusFromPayload != null && !statusFromPayload.isBlank()) {
            return statusFromPayload.trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String mapAsaasChargeStatus(String normalizedStatus) {
        if (normalizedStatus == null) {
            return null;
        }
        return switch (normalizedStatus) {
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> "AWAITING_PAYMENT";
            case "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> "PAID";
            case "OVERDUE" -> "EXPIRED";
            case "REFUNDED", "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "CHARGEBACK_REVERSED" -> "FAILED";
            case "CANCELED" -> "CANCELED";
            default -> null;
        };
    }

    private TransactionStatus mapAsaasTransactionStatus(String normalizedStatus) {
        if (normalizedStatus == null) {
            return null;
        }
        return switch (normalizedStatus) {
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> TransactionStatus.PENDING;
            case "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> TransactionStatus.PAID;
            case "OVERDUE" -> TransactionStatus.EXPIRED;
            case "CANCELED" -> TransactionStatus.CANCELED;
            case "REFUNDED", "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "CHARGEBACK_REVERSED" -> TransactionStatus.FAILED;
            default -> null;
        };
    }

    private boolean isPaidStatus(String normalizedStatus) {
        return "RECEIVED".equals(normalizedStatus)
                || "CONFIRMED".equals(normalizedStatus)
                || "RECEIVED_IN_CASH".equals(normalizedStatus);
    }

    private List<EfiPixEvent> extractEfiPixEvents(JsonNode root) {
        List<EfiPixEvent> out = new ArrayList<>();

        JsonNode pixNode = root.path("pix");
        if (pixNode.isArray()) {
            for (JsonNode item : pixNode) {
                out.add(new EfiPixEvent(
                        text(item, "txid"),
                        text(item, "endToEndId"),
                        text(item, "status"),
                        text(item, "valor")
                ));
            }
        }

        String txid = text(root, "txid");
        if (txid != null) {
            out.add(new EfiPixEvent(
                    txid,
                    text(root, "endToEndId"),
                    text(root, "status"),
                    text(root, "valor")
            ));
        }

        JsonNode dataNode = root.path("data");
        if (dataNode.isArray()) {
            for (JsonNode item : dataNode) {
                String txidData = text(item, "txid");
                if (txidData != null) {
                    out.add(new EfiPixEvent(
                            txidData,
                            text(item, "endToEndId"),
                            text(item, "status"),
                            text(item, "valor")
                    ));
                }
            }
        }

        return out;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String out = value.asText();
        return out == null || out.isBlank() ? null : out;
    }

    private record EfiPixEvent(
            String txid,
            String endToEndId,
            String status,
            String amount
    ) {}
}
