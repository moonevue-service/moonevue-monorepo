package com.moonevue.core.enums;

public enum TransactionType {
    CHARGE,     // Cobrança (ex.: boleto, pix, cartão)
    REFUND,     // Estorno
    PAYOUT,     // Pagamento para terceiros / saque
    TRANSFER,   // Transferência entre contas
    FEE         // Tarifas/ajustes
}
