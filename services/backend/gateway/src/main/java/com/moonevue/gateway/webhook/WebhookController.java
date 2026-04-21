package com.moonevue.gateway.webhook;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/banks")
@RequiredArgsConstructor
@Hidden // não aparece no Swagger
public class WebhookController {
    private final WebhookService svc;

    // Ex.: /webhooks/banks/{provider}/events
    @PostMapping("/{provider}/events")
    @PreAuthorize("hasAuthority('WEBHOOK')") // só passa se o filtro validar assinatura/caller
    public ResponseEntity<Void> receive(@PathVariable("provider") String provider,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                        @RequestBody String payload) {
        svc.handle(provider, payload, idemKey);
        return ResponseEntity.noContent().build();
    }
}
