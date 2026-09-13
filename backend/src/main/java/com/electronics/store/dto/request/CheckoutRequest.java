package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.electronics.store.entity.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Only receiverName (required, nonblank, max 150), phone (required, nonblank, max 30), shippingAddress (required, nonblank, max 500), note (optional, max 2000), paymentMethod (required COD or VNPAY) accepted. Unknown fields rejected; amounts/items/user are server-derived.")
public record CheckoutRequest(
        @NotBlank(message = "Receiver name is required") @Size(max = 150) String receiverName,
        @NotBlank(message = "Phone is required") @Size(max = 30) String phone,
        @NotBlank(message = "Shipping address is required") @Size(max = 500) String shippingAddress,
        @Size(max = 2000) String note,
        @NotNull(message = "Payment method is required") PaymentMethod paymentMethod
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported checkout field: " + name);
    }
}
