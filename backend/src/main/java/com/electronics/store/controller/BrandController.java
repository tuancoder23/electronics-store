package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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
    @Operation(summary = "List brands",
            description = "PUBLIC: no JWT or role required. Returns all records. No pagination or body.",
            tags = {"Brand"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {
        List<BrandResponse> brands = brandService.getAllBrands();
        return ResponseEntity.ok(ApiResponse.ok("Brands retrieved successfully", brands));
    }

    /**
     * Public endpoint: Get brand by ID.
     * GET /api/brands/{id}
     */
    @Operation(summary = "Get brand",
            description = "PUBLIC: no JWT or role required. Returns record by id; missing record 404.",
            tags = {"Brand"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/brands/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(@io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id) {
        BrandResponse brand = brandService.getBrandById(id);
        return ResponseEntity.ok(ApiResponse.ok("Brand retrieved successfully", brand));
    }

    /**
     * Admin endpoint: Create a new brand.
     * POST /api/admin/brands
     */
    @Operation(summary = "Create brand",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Validates request schema, trims name and generates unique slug. Duplicate name 409.",
            tags = {"Brand"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
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
    @Operation(summary = "Update brand",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Replaces editable fields using request schema. Name unique; missing resource/reference 404.",
            tags = {"Brand"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/admin/brands/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id,
            @Valid @RequestBody BrandRequest request
    ) {
        BrandResponse updated = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Brand updated successfully", updated));
    }

    /**
     * Admin endpoint: Delete a brand.
     * DELETE /api/admin/brands/{id}
     */
    @Operation(summary = "Delete brand",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Deletes by id. HTTP 200 omits data. Database relationships may prevent deletion (500).",
            tags = {"Brand"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @DeleteMapping("/admin/brands/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.ok("Brand deleted successfully"));
    }
}
