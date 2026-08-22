package com.electronics.store.mapper;

import com.electronics.store.dto.request.ProductImageRequest;
import com.electronics.store.dto.response.ProductImageResponse;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductImageEntity;
import org.springframework.stereotype.Component;

@Component
public class ProductImageMapper {

    public ProductImageResponse toResponse(ProductImageEntity entity) {
        if (entity == null) {
            return null;
        }

        Long productId = entity.getProduct() != null ? entity.getProduct().getId() : null;

        return new ProductImageResponse(
                entity.getId(),
                productId,
                entity.getImageUrl(),
                entity.getAltText(),
                entity.isPrimary(),
                entity.getDisplayOrder(),
                entity.getCreatedAt()
        );
    }

    public ProductImageEntity toEntity(ProductImageRequest request, ProductEntity product, boolean isPrimary) {
        if (request == null) {
            return null;
        }

        return ProductImageEntity.builder()
                .product(product)
                .imageUrl(request.imageUrl().trim())
                .altText(request.altText() != null ? request.altText().trim() : null)
                .primary(isPrimary)
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();
    }
}
