package com.electronics.store.mapper;

import com.electronics.store.dto.response.OrderResponse;
import com.electronics.store.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderMapper {
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;

    public OrderResponse toResponse(OrderEntity entity) {
        return new OrderResponse(entity.getId(), entity.getReceiverName(), entity.getPhone(),
                entity.getShippingAddress(), entity.getNote(), entity.getSubtotal(), entity.getShippingFee(),
                entity.getTotalAmount(), entity.getStatus(), entity.getPaymentMethod(),
                entity.getItems().stream().map(orderItemMapper::toResponse).toList(),
                entity.getCreatedAt(), entity.getUpdatedAt(), paymentMapper.toResponse(entity.getPayment()));
    }
}
