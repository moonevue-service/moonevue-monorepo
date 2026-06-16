package com.moonevue.gateway.service;

public class CheckoutPaymentProcessingException extends Exception {
    public CheckoutPaymentProcessingException(String message) {
        super(message);
    }

    public CheckoutPaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}