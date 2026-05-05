package com.moonevue.gateway.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Informações públicas do checkout, retornadas ao cliente final.
 * Não expõe dados sensíveis do tenant (extraConfig, certificados, etc.).
 */
public record CheckoutInfoDTO(
        UUID token,
        BigDecimal amount,
        String description,
        List<String> allowedInstruments,
        String status,
        OffsetDateTime expiresAt,
        String checkoutAccessMode,
        Long clientId,
        String clientName,
        String clientDocumentMasked,
        boolean identityVerified,
        /** Banco emissor (ex: EFI) — só para exibição */
        String bank,
        /** Preenchido após pagamento bem-sucedido */
        ChargeResponseDTO paymentResult
) {}
