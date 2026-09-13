package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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

    @Operation(summary = "List my wishlist",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Only current user items. page >= 0, size 1..100. createdAt DESC then id DESC.",
            tags = {"Wishlist"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<WishlistItemResponse>>> getWishlist(
            @io.swagger.v3.oas.annotations.Parameter(description = "Zero-based page number, minimum 0.") @RequestParam(defaultValue = "0") int page, @io.swagger.v3.oas.annotations.Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Wishlist retrieved successfully",
                wishlistService.getCurrentUserWishlist(page, size)));
    }

    @Operation(summary = "Add product to wishlist",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Positive existing productId required. Duplicate returns existing item (200). No body.",
            tags = {"Wishlist"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addProduct(@io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Product is in wishlist", wishlistService.addProduct(productId)));
    }

    @Operation(summary = "Remove wishlist product",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Positive productId. Only current user wishlist affected. Missing entry successful no-op (200 without data). No body.",
            tags = {"Wishlist"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeProduct(@io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId) {
        wishlistService.removeProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok("Product removed from wishlist"));
    }
}
