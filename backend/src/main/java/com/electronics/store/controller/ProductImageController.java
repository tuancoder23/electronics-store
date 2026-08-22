package com.electronics.store.controller;

import com.electronics.store.dto.request.ProductImageRequest;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.ProductImageResponse;
import com.electronics.store.service.ProductImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller handling public and administrative Product Image endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    /**
     * Public endpoint: Get all images for a product.
     * GET /api/products/{productId}/images
     */
    @GetMapping("/products/{productId}/images")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImagesByProductId(
            @PathVariable Long productId
    ) {
        List<ProductImageResponse> images = productImageService.getImagesByProductId(productId);
        return ResponseEntity.ok(ApiResponse.ok("Product images retrieved successfully", images));
    }

    /**
     * Public endpoint: Get product image by ID.
     * GET /api/product-images/{imageId}
     */
    @GetMapping("/product-images/{imageId}")
    public ResponseEntity<ApiResponse<ProductImageResponse>> getImageById(
            @PathVariable Long imageId
    ) {
        ProductImageResponse image = productImageService.getImageById(imageId);
        return ResponseEntity.ok(ApiResponse.ok("Product image retrieved successfully", image));
    }

    /**
     * Admin endpoint: Add a new image to a product.
     * POST /api/admin/products/{productId}/images
     */
    @PostMapping("/admin/products/{productId}/images")
    public ResponseEntity<ApiResponse<ProductImageResponse>> addImage(
            @PathVariable Long productId,
            @Valid @RequestBody ProductImageRequest request
    ) {
        ProductImageResponse created = productImageService.addImage(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product image added successfully", created));
    }

    /**
     * Admin endpoint: Update a product image.
     * PUT /api/admin/product-images/{imageId}
     */
    @PutMapping("/admin/product-images/{imageId}")
    public ResponseEntity<ApiResponse<ProductImageResponse>> updateImage(
            @PathVariable Long imageId,
            @Valid @RequestBody ProductImageRequest request
    ) {
        ProductImageResponse updated = productImageService.updateImage(imageId, request);
        return ResponseEntity.ok(ApiResponse.ok("Product image updated successfully", updated));
    }

    /**
     * Admin endpoint: Set image as primary.
     * PUT /api/admin/product-images/{imageId}/primary
     */
    @PutMapping("/admin/product-images/{imageId}/primary")
    public ResponseEntity<ApiResponse<ProductImageResponse>> setPrimaryImage(
            @PathVariable Long imageId
    ) {
        ProductImageResponse updated = productImageService.setPrimaryImage(imageId);
        return ResponseEntity.ok(ApiResponse.ok("Primary image updated successfully", updated));
    }

    /**
     * Admin endpoint: Delete a product image.
     * DELETE /api/admin/product-images/{imageId}
     */
    @DeleteMapping("/admin/product-images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long imageId
    ) {
        productImageService.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.ok("Product image deleted successfully"));
    }
}
