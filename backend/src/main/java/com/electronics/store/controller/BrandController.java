package com.electronics.store.controller;

import com.electronics.store.dto.request.BrandRequest;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.BrandResponse;
import com.electronics.store.service.BrandService;
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
 * Controller handling public and administrative Brand endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /**
     * Public endpoint: Get all brands.
     * GET /api/brands
     */
    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {
        List<BrandResponse> brands = brandService.getAllBrands();
        return ResponseEntity.ok(ApiResponse.ok("Brands retrieved successfully", brands));
    }

    /**
     * Public endpoint: Get brand by ID.
     * GET /api/brands/{id}
     */
    @GetMapping("/brands/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(@PathVariable Long id) {
        BrandResponse brand = brandService.getBrandById(id);
        return ResponseEntity.ok(ApiResponse.ok("Brand retrieved successfully", brand));
    }

    /**
     * Admin endpoint: Create a new brand.
     * POST /api/admin/brands
     */
    @PostMapping("/admin/brands")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody BrandRequest request) {
        BrandResponse created = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Brand created successfully", created));
    }

    /**
     * Admin endpoint: Update an existing brand.
     * PUT /api/admin/brands/{id}
     */
    @PutMapping("/admin/brands/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandRequest request
    ) {
        BrandResponse updated = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Brand updated successfully", updated));
    }

    /**
     * Admin endpoint: Delete a brand.
     * DELETE /api/admin/brands/{id}
     */
    @DeleteMapping("/admin/brands/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.ok("Brand deleted successfully"));
    }
}
