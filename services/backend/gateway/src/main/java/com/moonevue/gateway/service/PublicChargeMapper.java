package com.moonevue.gateway.service;

import com.moonevue.core.entity.Charge;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.dto.PublicChargeRequest;
import com.moonevue.gateway.dto.PublicChargeResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

/**
 * Converte o contrato público de cobrança para o DTO interno específico de provedor,
 * e mapeia as respostas/entidades de volta para o contrato público.
 */
@Component
public class PublicChargeMapper {

    private static final String TXID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int DEFAULT_PIX_EXPIRATION_SECONDS = 3600;
    private final SecureRandom random = new SecureRandom();

    public ChargeRequestDTO toChargeRequest(PublicChargeRequest request) {
        validate(request);
        PublicChargeRequest.Method method = request.method();
        ChargeRequestDTO.Payment payment = switch (method) {
            case PIX_IMMEDIATE -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.PIX_IMMEDIATE,
                    buildPixImmediate(request),
                    null,
                    null
            );
            case PIX_DUE -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.PIX_DUE,
                    null,
                    buildPixDue(request),
                    null
            );
            case BOLETO -> new ChargeRequestDTO.Payment(
                    ChargeRequestDTO.Instrument.BOLETO,
                    null,
                    null,
                    buildBoleto(request)
            );
        };
        return new ChargeRequestDTO(request.bank(), request.bankConfigurationId(), payment);
    }

    public PublicChargeResponse toPublicResponse(PublicChargeRequest request, ChargeResponseDTO response) {
        PublicChargeResponse.Pix pix = null;
        PublicChargeResponse.Boleto boleto = null;

        if (response.getPixCopiaECola() != null || response.getLocation() != null) {
            pix = new PublicChargeResponse.Pix(
                    response.getPixCopiaECola(),
                    response.getLocation(),
                    response.getExpiracao()
            );
        }
        if (response.getBarcode() != null || response.getPdfLink() != null || response.getLink() != null) {
            boleto = new PublicChargeResponse.Boleto(
                    response.getBarcode(),
                    response.getPdfLink(),
                    response.getBilletLink() != null ? response.getBilletLink() : response.getLink()
            );
        }

        return new PublicChargeResponse(
                response.getId(),
                response.getStatus(),
                request.method().name(),
                response.getProvider() != null ? response.getProvider().name() : null,
                request.amount(),
                response.getCurrency(),
                request.externalReference(),
                pix,
                boleto,
                null
        );
    }

    public PublicChargeResponse toPublicResponse(Charge charge) {
        PublicChargeResponse.Pix pix = charge.getPixCopyPaste() != null
                ? new PublicChargeResponse.Pix(charge.getPixCopyPaste(), null, null)
                : null;
        PublicChargeResponse.Boleto boleto = (charge.getBoletoLine() != null || charge.getBoletoPdfRef() != null)
                ? new PublicChargeResponse.Boleto(charge.getBoletoLine(), charge.getBoletoPdfRef(), null)
                : null;

        String publicId = charge.getProviderTxid() != null ? charge.getProviderTxid() : charge.getProviderChargeId();

        return new PublicChargeResponse(
                publicId,
                charge.getStatus(),
                charge.getPaymentMethod(),
                charge.getProvider(),
                charge.getAmountTotal(),
                "BRL",
                null,
                pix,
                boleto,
                charge.getCreatedAt() != null ? charge.getCreatedAt().toString() : null
        );
    }

    private void validate(PublicChargeRequest request) {
        if (request.method() == null) {
            throw new IllegalArgumentException("method é obrigatório (PIX_IMMEDIATE, PIX_DUE ou BOLETO)");
        }
        if (request.bank() == null) {
            throw new IllegalArgumentException("bank é obrigatório");
        }
        if (request.bankConfigurationId() == null) {
            throw new IllegalArgumentException("bankConfigurationId é obrigatório");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount deve ser maior que zero");
        }
        if ((request.method() == PublicChargeRequest.Method.PIX_DUE
                || request.method() == PublicChargeRequest.Method.BOLETO)
                && request.dueDate() == null) {
            throw new IllegalArgumentException("dueDate é obrigatório para " + request.method());
        }
    }

    private ChargeRequestDTO.PixImmediate buildPixImmediate(PublicChargeRequest request) {
        Doc doc = splitDocument(request.customer());
        String name = request.customer() != null ? request.customer().name() : null;
        return new ChargeRequestDTO.PixImmediate(
                DEFAULT_PIX_EXPIRATION_SECONDS,
                doc.cpf(), doc.cnpj(), name,
                request.amount(),
                request.description(),
                request.pixKey()
        );
    }

    private ChargeRequestDTO.PixDue buildPixDue(PublicChargeRequest request) {
        Doc doc = splitDocument(request.customer());
        String name = request.customer() != null ? request.customer().name() : null;
        return new ChargeRequestDTO.PixDue(
                generateTxid(),
                request.dueDate(),
                null,
                doc.cpf(), doc.cnpj(), name,
                null, null, null, null,
                request.amount(),
                null, null,
                null, null,
                request.description(),
                request.pixKey()
        );
    }

    private ChargeRequestDTO.Boleto buildBoleto(PublicChargeRequest request) {
        int valueInCents = request.amount().movePointRight(2).intValueExact();
        String itemName = request.description() != null && !request.description().isBlank()
                ? request.description()
                : "Cobrança";
        ChargeRequestDTO.Boleto.Item item =
                new ChargeRequestDTO.Boleto.Item(itemName, valueInCents, 1);

        Doc doc = splitDocument(request.customer());
        PublicChargeRequest.Customer c = request.customer();
        ChargeRequestDTO.Boleto.Customer.Juridical juridical = doc.cnpj() != null
                ? new ChargeRequestDTO.Boleto.Customer.Juridical(c != null ? c.name() : null, doc.cnpj())
                : null;
        ChargeRequestDTO.Boleto.Customer customer = new ChargeRequestDTO.Boleto.Customer(
                c != null ? c.name() : null,
                doc.cpf(),
                c != null ? c.email() : null,
                c != null ? c.phone() : null,
                juridical,
                null
        );

        return new ChargeRequestDTO.Boleto(
                List.of(item),
                customer,
                request.dueDate(),
                null,
                request.description()
        );
    }

    private record Doc(String cpf, String cnpj) {}

    private Doc splitDocument(PublicChargeRequest.Customer customer) {
        if (customer == null || customer.document() == null) {
            return new Doc(null, null);
        }
        String digits = customer.document().replaceAll("\\D", "");
        if (digits.length() == 14) {
            return new Doc(null, digits);
        }
        if (digits.length() == 11) {
            return new Doc(digits, null);
        }
        return new Doc(digits.isEmpty() ? null : digits, null);
    }

    private String generateTxid() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(TXID_ALPHABET.charAt(random.nextInt(TXID_ALPHABET.length())));
        }
        return sb.toString();
    }
}
