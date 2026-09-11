package com.electronics.store.mapper;

import com.electronics.store.dto.response.CartItemResponse;
import com.electronics.store.dto.response.CartResponse;
import com.electronics.store.entity.CartEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CartMapper {
    public CartResponse toResponse(CartEntity cart, List<CartItemResponse> items, int totalItems, BigDecimal subtotal) {
        return new CartResponse(cart.getId(), items, totalItems, subtotal, cart.getCreatedAt(), cart.getUpdatedAt());
    }
}
