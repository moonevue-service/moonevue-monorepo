package com.moonevue.gateway.dto;

import com.moonevue.core.enums.BankType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO único de entrada do seu sistema para gerar cobrança.
 * "config" direciona a integração; "payment" define o instrumento e os dados.
 */
public record ChargeRequestDTO(
        // Config do roteamento/integração
        BankType bank,
        Long bankConfigurationId,

        // Dados do pagamento
        Payment payment
) {
    public enum Instrument {
        PIX_IMMEDIATE, // POST /v2/cob
        PIX_DUE,       // PUT /v2/cobv/{txid}
        BOLETO         // POST /v1/charge/one-step
    }

    public static record Payment(
            Instrument instrument,
            PixImmediate pixImmediate,
            PixDue pixDue,
            Boleto boleto
    ) {}

    // PIX Imediata (/v2/cob)
    public static record PixImmediate(
            Integer expiracaoSeconds,
            String cpf, String cnpj, String nome, // um dos doc
            BigDecimal amount,                    // valor.original
            String solicitacaoPagador,            // opcional
            String chave                          // opcional: se nulo, usaremos pixKey do extraConfig
    ) {}

    // PIX com vencimento (/v2/cobv/{txid})
    public static record PixDue(
            String txid,
            LocalDate dataDeVencimento,
            Integer validadeAposVencimento,
            // Devedor endereço opcional
            String cpf, String cnpj, String nome,
            String logradouro, String cidade, String uf, String cep,
            BigDecimal amountOriginal,
            // Multa/Juros/Desconto simplificados (valores percentuais como string conforme API Efí)
            String multaPerc, String jurosPerc,
            LocalDate descontoData, String descontoValorPerc,
            String solicitacaoPagador,
            String chave // opcional: se nulo, usa pixKey do extraConfig
    ) {}

    // BOLETO (Cobranças /v1/charge/one-step)
    public static record Boleto(
            List<Item> items,
            Customer customer,
            LocalDate expireAt,
            Configurations configurations,
            String message
    ) {
        public static record Item(String name, Integer valueInCents, Integer amount) {}
        public static record Customer(
                String name, String cpf,
                String email, String phoneNumber,
                Juridical juridicalPerson,
                Address address
        ) {
            public static record Juridical(String corporateName, String cnpj) {}
            public static record Address(
                    String street, String number, String neighborhood, String zipcode,
                    String city, String complement, String state
            ) {}
        }
        public static record Configurations(
                Integer fineInCents,
                // interest pode ser inteiro (ao mês, em centavos) ou estrutura para type=monthly
                Integer interestInCents,
                Integer daysToWriteOff,
                Map<String, Object> interestObject // permite {"value":330,"type":"monthly"}
        ) {}
    }
}
