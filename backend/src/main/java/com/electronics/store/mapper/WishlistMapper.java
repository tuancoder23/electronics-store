package com.electronics.store.mapper;

import com.electronics.store.dto.response.ProductResponse;
import com.electronics.store.dto.response.WishlistItemResponse;
import com.electronics.store.entity.WishlistItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WishlistMapper {
    private final ProductMapper productMapper;

    public WishlistItemResponse toResponse(WishlistItemEntity entity) {
        ProductResponse product = productMapper.toResponse(entity.getProduct());
        return new WishlistItemResponse(entity.getId(), product.id(), product.name(), product.slug(),
                product.price(), product.discountPrice(), product.thumbnailUrl(), product.status(),
                product.brand(), product.category(), entity.getCreatedAt());
    }
}
