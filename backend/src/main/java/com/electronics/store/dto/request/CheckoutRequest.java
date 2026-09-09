package com.electronics.store.dto.request;

import com.electronics.store.entity.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank(message = "Receiver name is required") @Size(max = 150) String receiverName,
        @NotBlank(message = "Phone is required") @Size(max = 30) String phone,
        @NotBlank(message = "Shipping address is required") @Size(max = 500) String shippingAddress,
        @Size(max = 2000) String note,
        @NotNull(message = "Payment method is required; only COD is supported") PaymentMethod paymentMethod
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported checkout field: " + name);
    }
}
