package com.moonevue.gateway.dto;

public record CheckoutClientLookupDTO(
        boolean found,
        String name,
        String email,
        String phone
) {}
