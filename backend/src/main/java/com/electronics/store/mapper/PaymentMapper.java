package com.electronics.store.mapper;

import com.electronics.store.dto.response.PaymentResponse;
import com.electronics.store.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    public PaymentResponse toResponse(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaymentResponse(entity.getId(), entity.getMethod(), entity.getStatus(), entity.getAmount(),
                entity.getTransactionCode(), entity.getPaidAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
