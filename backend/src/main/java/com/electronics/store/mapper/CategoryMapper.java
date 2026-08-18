package com.electronics.store.mapper;

import com.electronics.store.dto.request.CategoryRequest;
import com.electronics.store.dto.response.CategoryResponse;
import com.electronics.store.entity.CategoryEntity;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(CategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CategoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public CategoryEntity toEntity(CategoryRequest request, String slug) {
        if (request == null) {
            return null;
        }
        return CategoryEntity.builder()
                .name(request.name().trim())
                .slug(slug)
                .description(request.description() != null ? request.description().trim() : null)
                .build();
    }
}
