package com.electronics.store.mapper;

import com.electronics.store.dto.response.CartItemResponse;
import com.electronics.store.entity.CartItemEntity;
import com.electronics.store.entity.ProductEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CartItemMapper {
    public CartItemResponse toResponse(CartItemEntity entity, BigDecimal effectivePrice, BigDecimal lineTotal) {
        ProductEntity product = entity.getProduct();
        return new CartItemResponse(
                entity.getId(), product.getId(), product.getName(), product.getSlug(), product.getThumbnailUrl(),
                product.getPrice(), product.getDiscountPrice(), effectivePrice, entity.getQuantity(),
                lineTotal
        );
    }
}
