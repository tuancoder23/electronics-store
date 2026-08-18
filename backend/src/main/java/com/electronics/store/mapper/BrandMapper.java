package com.electronics.store.mapper;

import com.electronics.store.dto.request.BrandRequest;
import com.electronics.store.dto.response.BrandResponse;
import com.electronics.store.entity.BrandEntity;
import org.springframework.stereotype.Component;

@Component
public class BrandMapper {

    public BrandResponse toResponse(BrandEntity entity) {
        if (entity == null) {
            return null;
        }
        return new BrandResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getLogoUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public BrandEntity toEntity(BrandRequest request, String slug) {
        if (request == null) {
            return null;
        }
        return BrandEntity.builder()
                .name(request.name().trim())
                .slug(slug)
                .description(request.description() != null ? request.description().trim() : null)
                .logoUrl(request.logoUrl() != null ? request.logoUrl().trim() : null)
                .build();
    }
}
