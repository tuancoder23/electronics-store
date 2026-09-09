package com.electronics.store.dto.response;

import com.electronics.store.entity.PaymentMethod;
import com.electronics.store.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        String transactionCode,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
