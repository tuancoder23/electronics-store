package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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
    @Operation(summary = "List product specifications",
            description = "PUBLIC: no JWT or role required. Existing product required. Ordered by displayOrder then id.",
            tags = {"Product Specification"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/products/{productId}/specifications")
    public ResponseEntity<ApiResponse<List<ProductSpecificationResponse>>> getSpecificationsByProductId(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId
    ) {
        List<ProductSpecificationResponse> specifications = productSpecificationService.getSpecificationsByProductId(productId);
        return ResponseEntity.ok(ApiResponse.ok("Specifications retrieved successfully", specifications));
    }

    /**
     * Public endpoint: Get specification by ID.
     * GET /api/product-specifications/{id}
     */
    @Operation(summary = "Get specification",
            description = "PUBLIC: no JWT or role required. Returns specification by id.",
            tags = {"Product Specification"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/product-specifications/{id}")
    public ResponseEntity<ApiResponse<ProductSpecificationResponse>> getSpecificationById(
            @io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id
    ) {
        ProductSpecificationResponse specification = productSpecificationService.getSpecificationById(id);
        return ResponseEntity.ok(ApiResponse.ok("Specification retrieved successfully", specification));
    }

    /**
     * Admin endpoint: Create a specification for a product.
     * POST /api/admin/products/{productId}/specifications
     */
    @Operation(summary = "Create specification",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Existing product required. Name unique within product. displayOrder defaults to 0.",
            tags = {"Product Specification"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PostMapping("/admin/products/{productId}/specifications")
    public ResponseEntity<ApiResponse<ProductSpecificationResponse>> createSpecification(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId,
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
    @Operation(summary = "Update specification",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Updates name/value/displayOrder. Name unique within product.",
            tags = {"Product Specification"},
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
    @PutMapping("/admin/product-specifications/{id}")
    public ResponseEntity<ApiResponse<ProductSpecificationResponse>> updateSpecification(
            @io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id,
            @Valid @RequestBody ProductSpecificationRequest request
    ) {
        ProductSpecificationResponse updated = productSpecificationService.updateSpecification(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Specification updated successfully", updated));
    }

    /**
     * Admin endpoint: Delete a specification by ID.
     * DELETE /api/admin/product-specifications/{id}
     */
    @Operation(summary = "Delete specification",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Deletes by id. HTTP 200 omits data.",
            tags = {"Product Specification"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @DeleteMapping("/admin/product-specifications/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSpecification(@io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id) {
        productSpecificationService.deleteSpecification(id);
        return ResponseEntity.ok(ApiResponse.ok("Specification deleted successfully"));
    }
}
