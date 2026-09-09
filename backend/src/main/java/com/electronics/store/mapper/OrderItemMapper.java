package com.electronics.store.mapper;

import com.electronics.store.dto.response.OrderItemResponse;
import com.electronics.store.entity.OrderItemEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderItemMapper {
    public OrderItemResponse toResponse(OrderItemEntity entity) {
        return new OrderItemResponse(entity.getId(), entity.getProductId(), entity.getProductName(),
                entity.getUnitPrice(), entity.getQuantity(), entity.getLineTotal());
    }
}
