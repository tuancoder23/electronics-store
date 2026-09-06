package com.electronics.store.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        Integer totalItems,
        BigDecimal subtotal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
