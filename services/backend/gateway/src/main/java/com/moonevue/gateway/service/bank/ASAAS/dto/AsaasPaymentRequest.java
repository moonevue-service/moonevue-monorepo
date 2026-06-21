package com.moonevue.gateway.service.bank.ASAAS.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.moonevue.gateway.service.bank.ASAAS.model.AsaasBillingType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo da requisição de criação de cobrança da ASAAS ({@code POST /v3/payments}).
 *
 * <p>Espelha o schema {@code PaymentSaveRequestDTO}. Campos nulos são omitidos na
 * serialização para respeitar a regra da ASAAS de que objetos vazios de
 * {@code interest}/{@code fine} podem sobrescrever configurações globais da conta.
 *
 * <p>Campos obrigatórios: {@code customer}, {@code billingType}, {@code value},
 * {@code dueDate}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasPaymentRequest(
        String customer,
        AsaasBillingType billingType,
        BigDecimal value,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dueDate,
        String description,
        String externalReference,
        Integer daysAfterDueDateToRegistrationCancellation,
        Integer installmentCount,
        BigDecimal installmentValue,
        BigDecimal totalValue,
        Boolean postalService,
        Discount discount,
        Interest interest,
        Fine fine
) {

    public enum DiscountType { FIXED, PERCENTAGE }

    public enum FineType { FIXED, PERCENTAGE }

    /** Regras de desconto aplicáveis à cobrança. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Discount(BigDecimal value, Integer dueDateLimitDays, DiscountType type) {}

    /** Regras de juros (percentual ao mês) para pagamento após o vencimento. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Interest(BigDecimal value) {}

    /** Regras de multa para pagamento após o vencimento. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Fine(BigDecimal value, FineType type) {}
}
