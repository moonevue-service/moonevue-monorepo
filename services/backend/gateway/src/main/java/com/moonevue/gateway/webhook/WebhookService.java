package com.moonevue.gateway.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonevue.core.entity.Transaction;
import com.moonevue.core.entity.TransactionLog;
import com.moonevue.core.entity.WebhookEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String provider, String payload, String idemKey) {
        String normalizedProvider = provider == null ? "unknown" : provider.trim().toLowerCase();
        String eventKey = buildEventKey(normalizedProvider, payload, idemKey);

        if (webhookEventRepository.existsByProviderAndEventKey(normalizedProvider, eventKey)) {
            log.info("webhook duplicado provider={} eventKey={}", normalizedProvider, eventKey);
            return;
        }

        WebhookEvent event = new WebhookEvent();
        event.setProvider(normalizedProvider);
        event.setEventKey(eventKey);
        event.setPayload(payload == null ? "" : payload);
        webhookEventRepository.save(event);

        try {
            if (!"efi".equals(normalizedProvider)) {
                event.setProcessed(true);
                event.setResult("IGNORED");
                event.setMessage("Provider sem processador específico");
                event.setProcessedAt(OffsetDateTime.now());
                webhookEventRepository.save(event);
                return;
            }

            int updated = processEfiWebhook(payload, eventKey);
            event.setProcessed(true);
            event.setResult("PROCESSED");
            event.setMessage("Transações atualizadas=" + updated);
            event.setProcessedAt(OffsetDateTime.now());
            webhookEventRepository.save(event);
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
