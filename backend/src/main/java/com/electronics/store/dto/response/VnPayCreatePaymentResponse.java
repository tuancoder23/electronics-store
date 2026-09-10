package com.electronics.store.dto.response;

public record VnPayCreatePaymentResponse(Long orderId, Long paymentId, String paymentUrl) {
}
