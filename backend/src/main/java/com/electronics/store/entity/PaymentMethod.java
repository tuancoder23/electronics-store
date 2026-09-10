package com.electronics.store.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMethod {
    COD,
    VNPAY;

    @JsonCreator
    public static PaymentMethod fromJson(String value) {
        for (PaymentMethod method : values()) {
            if (method.name().equals(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unsupported payment method. Use COD or VNPAY");
    }
}
