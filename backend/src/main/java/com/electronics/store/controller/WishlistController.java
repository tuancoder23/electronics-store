package com.electronics.store.controller;

import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.dto.response.WishlistItemResponse;
import com.electronics.store.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {
    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<WishlistItemResponse>>> getWishlist(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Wishlist retrieved successfully",
                wishlistService.getCurrentUserWishlist(page, size)));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Product is in wishlist", wishlistService.addProduct(productId)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeProduct(@PathVariable Long productId) {
        wishlistService.removeProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok("Product removed from wishlist"));
    }
}
