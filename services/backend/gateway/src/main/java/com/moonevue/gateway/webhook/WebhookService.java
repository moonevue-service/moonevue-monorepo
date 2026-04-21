package com.moonevue.gateway.webhook;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    // Exemplo simples: repassar ao finance (poderia ser mensagem assíncrona)
    // Ideal: persistir evento + idempotência antes, e processar assíncrono.

    public void handle(String provider, String payload, String idemKey) {
        log.info("webhook provider={} idemKey={}", provider, idemKey);
        // TODO: gravar idempotência (se já processado, retornar sem erro)
        // Repassar ao finance
        restTemplate.postForEntity("http://finance:8080/internal/webhooks/banks/" + provider, payload, Void.class);
    }
}
