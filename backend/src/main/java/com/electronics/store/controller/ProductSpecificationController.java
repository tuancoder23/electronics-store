package com.electronics.store.controller;

import com.electronics.store.dto.request.ProductSpecificationRequest;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.ProductSpecificationResponse;
import com.electronics.store.service.ProductSpecificationService;
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
 * Controller handling public and administrative Product Specification endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductSpecificationController {

    private final ProductSpecificationService productSpecificationService;

    /**
     * Public endpoint: Get all specifications for a product.
     * GET /api/products/{productId}/specifications
     */
    @GetMapping("/products/{productId}/specifications")
    public ResponseEntity<ApiResponse<List<ProductSpecificationResponse>>> getSpecificationsByProductId(
            @PathVariable Long productId
    ) {
        List<ProductSpecificationResponse> specifications = productSpecificationService.getSpecificationsByProductId(productId);
        return ResponseEntity.ok(ApiResponse.ok("Specifications retrieved successfully", specifications));
    }

    /**
     * Public endpoint: Get specification by ID.
     * GET /api/product-specifications/{id}
     */
    @GetMapping("/product-specifications/{id}")
    public ResponseEntity<ApiResponse<ProductSpecificationResponse>> getSpecificationById(
            @PathVariable Long id
    ) {
        ProductSpecificationResponse specification = productSpecificationService.getSpecificationById(id);
        return ResponseEntity.ok(ApiResponse.ok("Specification retrieved successfully", specification));
    }

    /**
     * Admin endpoint: Create a specification for a product.
     * POST /api/admin/products/{productId}/specifications
     */
    @PostMapping("/admin/products/{productId}/specifications")
    public ResponseEntity<ApiResponse<ProductSpecificationResponse>> createSpecification(
            @PathVariable Long productId,
            @Valid @RequestBody ProductSpecificationRequest request
    ) {
        ProductSpecificationResponse created = productSpecificationService.createSpecification(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Specification created successfully", created));
    }

    /**
     * Admin endpoint: Update a specification by ID.
     * PUT /api/admin/product-specifications/{id}
     */
    @PutMapping("/admin/product-specifications/{id}")
    public ResponseEntity<ApiResponse<ProductSpecificationResponse>> updateSpecification(
            @PathVariable Long id,
            @Valid @RequestBody ProductSpecificationRequest request
    ) {
        ProductSpecificationResponse updated = productSpecificationService.updateSpecification(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Specification updated successfully", updated));
    }

    /**
     * Admin endpoint: Delete a specification by ID.
     * DELETE /api/admin/product-specifications/{id}
     */
    @DeleteMapping("/admin/product-specifications/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSpecification(@PathVariable Long id) {
        productSpecificationService.deleteSpecification(id);
        return ResponseEntity.ok(ApiResponse.ok("Specification deleted successfully"));
    }
}
