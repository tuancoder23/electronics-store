package com.electronics.store.service;

import com.electronics.store.dto.request.AddCartItemRequest;
import com.electronics.store.dto.request.UpdateCartItemRequest;
import com.electronics.store.dto.response.CartResponse;

public interface CartService {
    CartResponse getCurrentUserCart();
    CartResponse addItem(AddCartItemRequest request);
    CartResponse updateItem(Long cartItemId, UpdateCartItemRequest request);
    CartResponse removeItem(Long cartItemId);
    CartResponse clearCart();
}
