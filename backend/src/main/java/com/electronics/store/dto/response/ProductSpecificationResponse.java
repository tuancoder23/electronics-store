package com.electronics.store.dto.response;

public record ProductSpecificationResponse(
        Long id,
        Long productId,
        String specName,
        String specValue,
        Integer displayOrder
) {
}
