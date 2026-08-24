package com.electronics.store.dto.request;

import com.electronics.store.entity.ProductStatus;

import java.math.BigDecimal;

public record ProductSearchCriteria(
        String keyword,
        Long categoryId,
        Long brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status,
        int page,
        int size,
        String sort
) {
}
