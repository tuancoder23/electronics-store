package com.electronics.store.dto.response;

import com.electronics.store.entity.PaymentStatus;

// Browser result and persisted payment status are separate: only IPN confirms payment.
public record VnPayReturnResponse(Long orderId, Long paymentId, PaymentStatus paymentStatus,
                                 String gatewayResponseCode, String gatewayTransactionStatus) {
}
