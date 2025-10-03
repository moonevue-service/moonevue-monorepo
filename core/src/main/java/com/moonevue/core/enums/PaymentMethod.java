package com.moonevue.core.enums;

public enum PaymentMethod {
    MONEY("Money"),
    CREDIT("Credit Card"),
    DEBIT("Debit"),
    PIX("PIX");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
