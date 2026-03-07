package com.moonevue.gateway.controller;

import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.PaymentRequestDTO;
import com.moonevue.gateway.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Recebe DTO tipado e delega a lógica de conversão/roteamento para o service
    @PostMapping
    public ResponseEntity<?> createPayment(@Valid @RequestBody ChargeRequestDTO request) {
        try {
            String response = paymentService.create(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Falha ao criar pagamento: " + e.getMessage());
        }
    }
}