package com.electronics.store.controller;

import com.electronics.store.dto.request.AddCartItemRequest;
import com.electronics.store.dto.request.UpdateCartItemRequest;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.CartResponse;
import com.electronics.store.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        return ResponseEntity.ok(ApiResponse.ok("Cart retrieved successfully", cartService.getCurrentUserCart()));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(@Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Item added to cart successfully", cartService.addItem(request)));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @PathVariable Long cartItemId, @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cart item updated successfully",
                cartService.updateItem(cartItemId, request)));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(@PathVariable Long cartItemId) {
        return ResponseEntity.ok(ApiResponse.ok("Cart item removed successfully", cartService.removeItem(cartItemId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<CartResponse>> clearCart() {
        return ResponseEntity.ok(ApiResponse.ok("Cart cleared successfully", cartService.clearCart()));
    }
}
