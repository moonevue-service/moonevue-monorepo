package com.moonevue.gateway.controller;

import com.moonevue.gateway.dto.CheckoutInfoDTO;
import com.moonevue.gateway.dto.CheckoutClientLookupDTO;
import com.moonevue.gateway.dto.CheckoutIdentifyRequest;
import com.moonevue.gateway.dto.CheckoutPayRequest;
import jakarta.validation.Valid;
import com.moonevue.gateway.service.CheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<?> getInfo(@PathVariable("token") UUID token) {
        try {
            CheckoutInfoDTO info = checkoutService.getInfo(token);
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[CheckoutController] Erro ao buscar checkout token={}: {}", token, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erro ao carregar link"));
        }
    }

    @GetMapping("/{token}/status")
    public ResponseEntity<?> getStatus(@PathVariable("token") UUID token) {
        try {
            CheckoutInfoDTO info = checkoutService.getStatus(token);
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[CheckoutController] Erro ao buscar status token={}: {}", token, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erro ao consultar status"));
        }
    }

    @GetMapping("/{token}/client-lookup")
    public ResponseEntity<?> lookupClient(
            @PathVariable("token") UUID token,
            @RequestParam("document") String document
    ) {
        try {
            CheckoutClientLookupDTO result = checkoutService.lookupClient(token, document);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[CheckoutController] Erro ao buscar cliente token={}: {}", token, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erro ao consultar cliente"));
        }
    }

    @PostMapping("/{token}/identify")
    public ResponseEntity<?> identify(@PathVariable("token") UUID token,
                                      @Valid @RequestBody CheckoutIdentifyRequest request) {
        try {
            CheckoutInfoDTO info = checkoutService.identify(token, request.document());
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[CheckoutController] Erro ao identificar cliente token={}: {}", token, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erro ao validar identidade"));
        }
    }

    @PostMapping("/{token}/pay")
    public ResponseEntity<?> pay(@PathVariable("token") UUID token, @RequestBody CheckoutPayRequest request) {
        try {
            CheckoutInfoDTO result = checkoutService.pay(token, request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[CheckoutController] Falha ao pagar checkout token={}: {}", token, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Falha ao processar pagamento", "detail", e.getMessage()));
        }
    }
}
