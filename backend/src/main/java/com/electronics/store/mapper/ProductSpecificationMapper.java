package com.electronics.store.mapper;

import com.electronics.store.dto.request.ProductSpecificationRequest;
import com.electronics.store.dto.response.ProductSpecificationResponse;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductSpecificationEntity;
import org.springframework.stereotype.Component;

@Component
public class ProductSpecificationMapper {

    public ProductSpecificationResponse toResponse(ProductSpecificationEntity entity) {
        if (entity == null) {
            return null;
        }

        Long productId = entity.getProduct() != null ? entity.getProduct().getId() : null;

        return new ProductSpecificationResponse(
                entity.getId(),
                productId,
                entity.getSpecName(),
                entity.getSpecValue(),
                entity.getDisplayOrder()
        );
    }

    public ProductSpecificationEntity toEntity(ProductSpecificationRequest request, ProductEntity product) {
        if (request == null) {
            return null;
        }

        return ProductSpecificationEntity.builder()
                .product(product)
                .specName(request.specName().trim())
                .specValue(request.specValue().trim())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();
    }
}
