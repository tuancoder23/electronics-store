package com.electronics.store.mapper;

import com.electronics.store.dto.response.CartItemResponse;
import com.electronics.store.entity.CartItemEntity;
import com.electronics.store.entity.ProductEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CartItemMapper {
    public CartItemResponse toResponse(CartItemEntity entity) {
        ProductEntity product = entity.getProduct();
        BigDecimal effectivePrice = effectivePrice(product);
        return new CartItemResponse(
                entity.getId(), product.getId(), product.getName(), product.getSlug(), product.getThumbnailUrl(),
                product.getPrice(), product.getDiscountPrice(), effectivePrice, entity.getQuantity(),
                effectivePrice.multiply(BigDecimal.valueOf(entity.getQuantity()))
        );
    }

    private BigDecimal effectivePrice(ProductEntity product) {
        BigDecimal discount = product.getDiscountPrice();
        return discount != null && discount.signum() >= 0 && discount.compareTo(product.getPrice()) < 0
                ? discount : product.getPrice();
    }
}
