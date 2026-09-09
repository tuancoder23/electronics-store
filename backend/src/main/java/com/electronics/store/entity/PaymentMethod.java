package com.electronics.store.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMethod {
    COD;

    @JsonCreator
    public static PaymentMethod fromJson(String value) {
        if (!"COD".equals(value)) {
            throw new IllegalArgumentException("Unsupported payment method. Only COD is supported");
        }
        return COD;
    }
}
