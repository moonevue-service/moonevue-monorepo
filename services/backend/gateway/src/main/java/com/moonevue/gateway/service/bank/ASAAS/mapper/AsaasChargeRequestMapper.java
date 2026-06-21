package com.moonevue.gateway.service.bank.ASAAS.mapper;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.service.bank.ASAAS.config.AsaasConfigKeys;
import com.moonevue.gateway.service.bank.ASAAS.dto.AsaasPaymentRequest;
import com.moonevue.gateway.service.bank.ASAAS.model.AsaasBillingType;
import com.moonevue.gateway.util.ExtraConfigUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Converte o modelo interno {@link ChargeRequestDTO} para o formato de criação de
 * cobrança da ASAAS ({@link AsaasPaymentRequest}).
 *
 * <p>O modelo interno foi modelado sobre a EFI (instrumentos PIX_IMMEDIATE,
 * PIX_DUE e BOLETO). Este mapper concentra as divergências entre os dois
 * provedores sem alterar a abstração compartilhada:
 *
 * <ul>
 *   <li>PIX_IMMEDIATE / PIX_DUE &rarr; {@code billingType = PIX}; BOLETO &rarr; {@code BOLETO}.</li>
 *   <li>{@code value} em reais (BigDecimal, 2 casas).</li>
 *   <li>{@code dueDate} no formato {@code yyyy-MM-dd}: hoje (PIX imediato),
 *       data de vencimento (PIX com vencimento) ou data de expiração (boleto).</li>
 *   <li>Multa/juros/desconto a partir dos campos percentuais de PIX com vencimento.</li>
 *   <li>{@code customer} resolvido do identificador ASAAS ({@code cus_...}) presente
 *       no request ou do padrão {@code asaas.customer} do extraConfig.</li>
 * </ul>
 */
@Component
public class AsaasChargeRequestMapper {

    private static final int DESCRIPTION_MAX_LENGTH = 500;

    public AsaasPaymentRequest toAsaasRequest(ChargeRequestDTO request, BankConfiguration cfg) {
        if (request == null || request.payment() == null || request.payment().instrument() == null) {
            throw new IllegalArgumentException("[ASAAS] Pagamento inválido: instrumento ausente.");
        }

        ChargeRequestDTO.Payment payment = request.payment();
        return switch (payment.instrument()) {
            case PIX_IMMEDIATE -> mapPixImmediate(payment.pixImmediate(), cfg);
            case PIX_DUE -> mapPixDue(payment.pixDue(), cfg);
            case BOLETO -> mapBoleto(payment.boleto(), cfg);
        };
    }

    // ===================== PIX imediato =====================

    private AsaasPaymentRequest mapPixImmediate(ChargeRequestDTO.PixImmediate p, BankConfiguration cfg) {
        if (p == null) {
            throw new IllegalArgumentException("[ASAAS] Dados de PIX imediato ausentes.");
        }
        String customer = resolveCustomer(cfg, p.cpf(), p.cnpj(), p.chave(), p.nome());
        BigDecimal value = requireValue(p.amount());
        return new AsaasPaymentRequest(
                customer,
                resolveBillingType(cfg, AsaasBillingType.PIX),
                value,
                LocalDate.now(),
                truncate(p.solicitacaoPagador()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    // ===================== PIX com vencimento =====================

    private AsaasPaymentRequest mapPixDue(ChargeRequestDTO.PixDue p, BankConfiguration cfg) {
        if (p == null) {
            throw new IllegalArgumentException("[ASAAS] Dados de PIX com vencimento ausentes.");
        }
        if (p.dataDeVencimento() == null) {
            throw new IllegalArgumentException("[ASAAS] dataDeVencimento é obrigatória para PIX com vencimento.");
        }
        String customer = resolveCustomer(cfg, p.cpf(), p.cnpj(), p.chave(), p.nome());
        BigDecimal value = requireValue(p.amountOriginal());

        return new AsaasPaymentRequest(
                customer,
                resolveBillingType(cfg, AsaasBillingType.PIX),
                value,
                p.dataDeVencimento(),
                truncate(p.solicitacaoPagador()),
                null,
                null,
                null,
                null,
                null,
                null,
                mapDiscount(p.descontoValorPerc(), p.descontoData(), p.dataDeVencimento()),
                mapInterest(p.jurosPerc()),
                mapFine(p.multaPerc())
        );
    }

    // ===================== Boleto =====================

    private AsaasPaymentRequest mapBoleto(ChargeRequestDTO.Boleto b, BankConfiguration cfg) {
        if (b == null) {
            throw new IllegalArgumentException("[ASAAS] Dados de boleto ausentes.");
        }
        if (b.expireAt() == null) {
            throw new IllegalArgumentException("[ASAAS] expireAt é obrigatório para boleto.");
        }
        String customerDoc = b.customer() != null ? b.customer().cpf() : null;
        String customerCnpj = b.customer() != null && b.customer().juridicalPerson() != null
                ? b.customer().juridicalPerson().cnpj()
                : null;
        String customerName = b.customer() != null ? b.customer().name() : null;
        String customer = resolveCustomer(cfg, customerDoc, customerCnpj, null, customerName);

        BigDecimal value = boletoTotal(b);
        Integer daysAfterDueDate = b.configurations() != null ? b.configurations().daysToWriteOff() : null;

        return new AsaasPaymentRequest(
                customer,
                resolveBillingType(cfg, AsaasBillingType.BOLETO),
                value,
                b.expireAt(),
                truncate(b.message()),
                null,
                daysAfterDueDate,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    // ===================== Helpers =====================

    /**
     * Resolve o identificador do cliente na ASAAS ({@code cus_...}).
     *
     * <p>Ordem de resolução:
     * <ol>
     *   <li>Qualquer campo do request que já contenha um id ASAAS ({@code cus_...});</li>
     *   <li>O padrão {@code asaas.customer} configurado no extraConfig.</li>
     * </ol>
     */
    private String resolveCustomer(BankConfiguration cfg, String... candidates) {
        if (candidates != null) {
            for (String candidate : candidates) {
                String trimmed = trimToNull(candidate);
                if (trimmed != null && trimmed.startsWith(AsaasConfigKeys.CUSTOMER_ID_PREFIX)) {
                    return trimmed;
                }
            }
        }
        String fromConfig = cfg != null
                ? trimToNull(ExtraConfigUtils.getString(cfg.getExtraConfig(), AsaasConfigKeys.CUSTOMER, null))
                : null;
        if (fromConfig != null) {
            return fromConfig;
        }
        throw new IllegalArgumentException(
                "[ASAAS] Customer é obrigatório: informe o id do cliente na ASAAS (cus_...) no request "
                        + "ou configure 'asaas.customer' no extraConfig da BankConfiguration.");
    }

    private AsaasBillingType resolveBillingType(BankConfiguration cfg, AsaasBillingType fallback) {
        String override = cfg != null
                ? trimToNull(ExtraConfigUtils.getString(cfg.getExtraConfig(), AsaasConfigKeys.BILLING_TYPE, null))
                : null;
        if (override == null) {
            return fallback;
        }
        try {
            return AsaasBillingType.valueOf(override.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("[ASAAS] billingType inválido em extraConfig: " + override);
        }
    }

    private AsaasPaymentRequest.Discount mapDiscount(String descontoValorPerc, LocalDate descontoData, LocalDate dueDate) {
        BigDecimal value = parsePercent(descontoValorPerc);
        if (value == null) {
            return null;
        }
        Integer dueDateLimitDays = null;
        if (descontoData != null && dueDate != null) {
            long days = ChronoUnit.DAYS.between(descontoData, dueDate);
            dueDateLimitDays = (int) Math.max(0, days);
        }
        return new AsaasPaymentRequest.Discount(value, dueDateLimitDays, AsaasPaymentRequest.DiscountType.PERCENTAGE);
    }

    private AsaasPaymentRequest.Interest mapInterest(String jurosPerc) {
        BigDecimal value = parsePercent(jurosPerc);
        return value == null ? null : new AsaasPaymentRequest.Interest(value);
    }

    private AsaasPaymentRequest.Fine mapFine(String multaPerc) {
        BigDecimal value = parsePercent(multaPerc);
        return value == null ? null : new AsaasPaymentRequest.Fine(value, AsaasPaymentRequest.FineType.PERCENTAGE);
    }

    private BigDecimal boletoTotal(ChargeRequestDTO.Boleto b) {
        if (b.items() == null || b.items().isEmpty()) {
            throw new IllegalArgumentException("[ASAAS] Boleto sem itens para cálculo do valor.");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeRequestDTO.Boleto.Item item : b.items()) {
            if (item.valueInCents() == null || item.amount() == null) {
                throw new IllegalArgumentException("[ASAAS] Item de boleto com valor/quantidade ausente.");
            }
            BigDecimal unit = BigDecimal.valueOf(item.valueInCents()).movePointLeft(2);
            total = total.add(unit.multiply(BigDecimal.valueOf(item.amount())));
        }
        return requireValue(total);
    }

    private BigDecimal requireValue(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("[ASAAS] value deve ser maior que zero.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercent(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("[ASAAS] Valor percentual inválido: " + raw);
        }
    }

    private String truncate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() > DESCRIPTION_MAX_LENGTH
                ? trimmed.substring(0, DESCRIPTION_MAX_LENGTH)
                : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
