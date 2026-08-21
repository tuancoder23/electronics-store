package com.electronics.store.mapper;

import com.electronics.store.dto.request.ProductRequest;
import com.electronics.store.dto.response.BrandSummaryResponse;
import com.electronics.store.dto.response.CategorySummaryResponse;
import com.electronics.store.dto.response.ProductResponse;
import com.electronics.store.entity.BrandEntity;
import com.electronics.store.entity.CategoryEntity;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductStatus;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(ProductEntity entity) {
        if (entity == null) {
            return null;
        }

        CategorySummaryResponse categorySummary = null;
        if (entity.getCategory() != null) {
            categorySummary = new CategorySummaryResponse(
                    entity.getCategory().getId(),
                    entity.getCategory().getName(),
                    entity.getCategory().getSlug()
            );
        }

        BrandSummaryResponse brandSummary = null;
        if (entity.getBrand() != null) {
            brandSummary = new BrandSummaryResponse(
                    entity.getBrand().getId(),
                    entity.getBrand().getName(),
                    entity.getBrand().getSlug()
            );
        }

        return new ProductResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getDiscountPrice(),
                entity.getQuantity(),
                entity.getThumbnailUrl(),
                entity.getStatus(),
                categorySummary,
                brandSummary,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ProductEntity toEntity(ProductRequest request, String slug, CategoryEntity category, BrandEntity brand) {
        if (request == null) {
            return null;
        }
        return ProductEntity.builder()
                .name(request.name().trim())
                .slug(slug)
                .description(request.description() != null ? request.description().trim() : null)
                .price(request.price())
                .discountPrice(request.discountPrice())
                .quantity(request.quantity())
                .thumbnailUrl(request.thumbnailUrl() != null ? request.thumbnailUrl().trim() : null)
                .status(request.status() != null ? request.status() : ProductStatus.ACTIVE)
                .category(category)
                .brand(brand)
                .build();
    }
}
