package com.electronics.store.mapper;

import com.electronics.store.dto.response.CartItemResponse;
import com.electronics.store.dto.response.CartResponse;
import com.electronics.store.entity.CartEntity;
import com.electronics.store.entity.CartItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CartMapper {
    private final CartItemMapper cartItemMapper;

    public CartResponse toResponse(CartEntity cart, List<CartItemEntity> entities) {
        List<CartItemResponse> items = entities.stream().map(cartItemMapper::toResponse).toList();
        int totalItems = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), items, totalItems, subtotal, cart.getCreatedAt(), cart.getUpdatedAt());
    }
}
