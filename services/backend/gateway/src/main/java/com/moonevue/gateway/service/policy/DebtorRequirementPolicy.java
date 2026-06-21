package com.moonevue.gateway.service.policy;

import com.moonevue.core.enums.BankType;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Política central que define, por provedor de pagamento e tipo de cobrança,
 * se o devedor/pagador (nome + documento) é obrigatório.
 *
 * <p>Esta é a fonte única da verdade usada tanto pelos fluxos de cobrança do
 * backend (PaymentService, CheckoutService, integrações) quanto exposta ao
 * frontend através do endpoint de capabilities. Centralizar a regra aqui evita
 * a divergência histórica entre validações espalhadas (DTOs com {@code @NotBlank},
 * checagens no frontend e omissões silenciosas nas integrações).
 *
 * <p>Regras EFI (confirmadas):
 * <ul>
 *   <li>PIX Imediato (/v2/cob): devedor OPCIONAL.</li>
 *   <li>PIX com Vencimento (/v2/cobv): devedor OBRIGATÓRIO (nome + CPF/CNPJ).</li>
 *   <li>Boleto (/v1/charge/one-step): pagador OBRIGATÓRIO (nome + CPF ou CNPJ).</li>
 * </ul>
 */
@Component
public class DebtorRequirementPolicy {

    /**
     * Indica se o devedor/pagador é obrigatório para o provedor e tipo informados.
     */
    public boolean isDebtorRequired(BankType provider, ChargeRequestDTO.Instrument chargeType) {
        if (provider == null || chargeType == null) {
            return false;
        }
        if (provider == BankType.EFI) {
            return switch (chargeType) {
                case PIX_IMMEDIATE -> false;
                case PIX_DUE -> true;
                case BOLETO -> true;
            };
        }
        // ASAAS identifica o pagador pelo customer id (cus_...), validado no
        // AsaasChargeRequestMapper. A obrigatoriedade de nome + documento no
        // request não se aplica a este provedor.
        if (provider == BankType.ASAAS) {
            return false;
        }
        // Provedores futuros: por padrão exige devedor (mais seguro). Ajustar quando integrados.
        return true;
    }

    /**
     * Mapa de requisitos por instrumento para um provedor. Usado pelo endpoint de capabilities
     * para que o frontend reflita exatamente as regras do backend.
     */
    public Map<ChargeRequestDTO.Instrument, Boolean> requirementsFor(BankType provider) {
        Map<ChargeRequestDTO.Instrument, Boolean> map = new LinkedHashMap<>();
        for (ChargeRequestDTO.Instrument instrument : ChargeRequestDTO.Instrument.values()) {
            map.put(instrument, isDebtorRequired(provider, instrument));
        }
        return map;
    }

    /**
     * Valida o devedor/pagador presente no request de acordo com a política.
     * Lança {@link IllegalArgumentException} (mapeada para HTTP 400 pelos controllers)
     * quando os dados obrigatórios estão ausentes ou inconsistentes.
     */
    public void validate(BankType provider, ChargeRequestDTO request) {
        if (request == null || request.payment() == null) {
            throw new IllegalArgumentException("Pagamento inválido: dados ausentes");
        }
        ChargeRequestDTO.Instrument instrument = request.payment().instrument();
        DebtorView debtor = extractDebtor(request.payment());

        boolean required = isDebtorRequired(provider, instrument);

        if (required) {
            if (isBlank(debtor.name())) {
                throw new IllegalArgumentException(
                        "Nome do devedor é obrigatório para " + describe(instrument) + ".");
            }
            if (isBlank(debtor.cpf()) && isBlank(debtor.cnpj())) {
                throw new IllegalArgumentException(
                        "CPF ou CNPJ do devedor é obrigatório para " + describe(instrument) + ".");
            }
        }

        // CPF e CNPJ são mutuamente exclusivos quando informados (regra oneOf da EFI).
        if (!isBlank(debtor.cpf()) && !isBlank(debtor.cnpj())) {
            throw new IllegalArgumentException(
                    "Informe apenas CPF ou apenas CNPJ do devedor, não ambos.");
        }
    }

    private DebtorView extractDebtor(ChargeRequestDTO.Payment payment) {
        return switch (payment.instrument()) {
            case PIX_IMMEDIATE -> {
                var p = payment.pixImmediate();
                yield p == null ? DebtorView.empty() : new DebtorView(p.nome(), p.cpf(), p.cnpj());
            }
            case PIX_DUE -> {
                var p = payment.pixDue();
                yield p == null ? DebtorView.empty() : new DebtorView(p.nome(), p.cpf(), p.cnpj());
            }
            case BOLETO -> {
                var b = payment.boleto();
                if (b == null || b.customer() == null) {
                    yield DebtorView.empty();
                }
                var c = b.customer();
                String cnpj = c.juridicalPerson() != null ? c.juridicalPerson().cnpj() : null;
                yield new DebtorView(c.name(), c.cpf(), cnpj);
            }
        };
    }

    private String describe(ChargeRequestDTO.Instrument instrument) {
        return switch (instrument) {
            case PIX_IMMEDIATE -> "PIX imediato";
            case PIX_DUE -> "PIX com vencimento";
            case BOLETO -> "boleto";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record DebtorView(String name, String cpf, String cnpj) {
        static DebtorView empty() {
            return new DebtorView(null, null, null);
        }
    }
}
