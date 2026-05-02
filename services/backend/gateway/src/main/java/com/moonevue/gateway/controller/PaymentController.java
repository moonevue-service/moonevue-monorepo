package com.moonevue.gateway.controller;

import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.dto.TransactionSummaryDTO;
import com.moonevue.gateway.service.PaymentService;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.security.IntrospectedAuthToken;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Recebe DTO tipado e delega a lógica de conversão/roteamento para o service
    @GetMapping
    public ResponseEntity<?> listTransactions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long tenantId = extractTenantId(authentication);
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant não identificado"));
        }
        Page<TransactionSummaryDTO> result = paymentService.listTransactions(tenantId, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createPayment(@Valid @RequestBody ChargeRequestDTO request) {
        return processCharge(request);
        }

        @PostMapping("/pix/immediate")
        public ResponseEntity<?> createPixImmediate(@Valid @RequestBody PixImmediateRequest request) {
        ChargeRequestDTO charge = new ChargeRequestDTO(
            request.bank(),
            request.bankConfigurationId(),
            new ChargeRequestDTO.Payment(
                ChargeRequestDTO.Instrument.PIX_IMMEDIATE,
                request.payment(),
                null,
                null
            )
        );

        return processCharge(charge);
        }

        @PostMapping("/pix/due")
        public ResponseEntity<?> createPixDue(@Valid @RequestBody PixDueRequest request) {
        ChargeRequestDTO charge = new ChargeRequestDTO(
            request.bank(),
            request.bankConfigurationId(),
            new ChargeRequestDTO.Payment(
                ChargeRequestDTO.Instrument.PIX_DUE,
                null,
                request.payment(),
                null
            )
        );

        return processCharge(charge);
        }

        @PostMapping("/boleto")
        public ResponseEntity<?> createBoleto(@Valid @RequestBody BoletoRequest request) {
        ChargeRequestDTO charge = new ChargeRequestDTO(
            request.bank(),
            request.bankConfigurationId(),
            new ChargeRequestDTO.Payment(
                ChargeRequestDTO.Instrument.BOLETO,
                null,
                null,
                request.payment()
            )
        );

        return processCharge(charge);
        }

    private Long extractTenantId(Authentication authentication) {
        if (authentication instanceof IntrospectedAuthToken token) {
            Object details = token.getDetails();
            if (details instanceof java.util.Map<?, ?> map) {
                Object tid = map.get("tenantId");
                if (tid instanceof Number n) return n.longValue();
            }
        }
        return null;
    }

        private ResponseEntity<?> processCharge(ChargeRequestDTO request) {
        try {
            ChargeResponseDTO response = paymentService.createCharge(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("[PaymentController] Requisição inválida: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("[PaymentController] Resposta inválida do provedor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Resposta inválida do provedor", "detail", e.getMessage()));
        } catch (Exception e) {
            log.error("[PaymentController] Falha ao criar pagamento: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Falha ao criar pagamento", "detail", e.getMessage()));
        }
    }

                public record PixImmediateRequest(
                    BankType bank,
                    Long bankConfigurationId,
                    ChargeRequestDTO.PixImmediate payment
                ) {}

                public record PixDueRequest(
                    BankType bank,
                    Long bankConfigurationId,
                    ChargeRequestDTO.PixDue payment
                ) {}

                public record BoletoRequest(
                    BankType bank,
                    Long bankConfigurationId,
                    ChargeRequestDTO.Boleto payment
                ) {}
}