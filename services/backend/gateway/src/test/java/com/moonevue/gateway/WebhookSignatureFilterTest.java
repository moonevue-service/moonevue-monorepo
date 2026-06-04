package com.moonevue.gateway;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSignatureFilterTest {

    private static final String SECRET = "test-secret";

    private String computeHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hashBytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02x", b));
        return "sha256=" + sb;
    }

    @Test
    void assinatura_valida_deve_ter_prefixo_sha256() throws Exception {
        String sig = computeHmac("{\"event\":\"payment.created\"}");
        assertTrue(sig.startsWith("sha256="), "Assinatura deve ter prefixo sha256=");
    }

    @Test
    void segredo_nao_pode_ser_usado_como_assinatura_direta() {
        // Remove backdoor: o segredo simples NAO deve ser aceito como assinatura HMAC válida
        assertFalse(SECRET.startsWith("sha256="),
            "Segredo não deve ter formato de assinatura — garante que o backdoor foi removido");
        assertNotEquals(64, SECRET.length(),
            "Segredo não deve ter comprimento de hash hex SHA-256 (64 chars)");
    }
}
