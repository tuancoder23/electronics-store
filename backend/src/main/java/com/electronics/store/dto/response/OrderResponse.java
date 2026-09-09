package com.electronics.store.dto.response;

import com.electronics.store.entity.OrderStatus;
import com.electronics.store.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String receiverName,
        String phone,
        String shippingAddress,
        String note,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        OrderStatus status,
        PaymentMethod paymentMethod,
        List<OrderItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        PaymentResponse payment
) {
}
