package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.electronics.store.dto.request.ProductRequest;
import com.electronics.store.dto.request.ProductSearchCriteria;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.dto.response.ProductResponse;
import com.electronics.store.entity.ProductStatus;
import com.electronics.store.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Controller handling public and administrative Product endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Public endpoint: Get all products.
     * GET /api/products
     */
    @Operation(summary = "Search products",
            description = "PUBLIC: no JWT or role required. Filters combined with AND; keyword matches product name case-insensitively. Price filter/sort uses regular price. No implicit ACTIVE filter. page >= 0, size 1..100, minPrice/maxPrice >= 0, minPrice <= maxPrice. sort=field,direction; fields price/name/createdAt; directions asc/desc. Default createdAt,desc; id breaks ties in same direction.",
            tags = {"Product Search / Filter / Sort / Pagination"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> getAllProducts(
            @io.swagger.v3.oas.annotations.Parameter(description = "Case-insensitive substring search; surrounding whitespace is trimmed.") @RequestParam(required = false) String keyword,
            @io.swagger.v3.oas.annotations.Parameter(description = "Filter by category identifier.") @RequestParam(required = false) Long categoryId,
            @io.swagger.v3.oas.annotations.Parameter(description = "Filter by brand identifier.") @RequestParam(required = false) Long brandId,
            @io.swagger.v3.oas.annotations.Parameter(description = "Inclusive minimum regular price, >= 0 and <= maxPrice.") @RequestParam(required = false) BigDecimal minPrice,
            @io.swagger.v3.oas.annotations.Parameter(description = "Inclusive maximum regular price, >= 0 and >= minPrice.") @RequestParam(required = false) BigDecimal maxPrice,
            @io.swagger.v3.oas.annotations.Parameter(description = "Optional exact enum name. Omit for all statuses.") @RequestParam(required = false) ProductStatus status,
            @io.swagger.v3.oas.annotations.Parameter(description = "Zero-based page number, minimum 0.") @RequestParam(defaultValue = "0") int page,
            @io.swagger.v3.oas.annotations.Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "12") int size,
            @io.swagger.v3.oas.annotations.Parameter(description = "field,direction: price/name/createdAt and asc/desc. Default createdAt,desc. id breaks ties.") @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                keyword, categoryId, brandId, minPrice, maxPrice, status, page, size, sort
        );
        PagedResponse<ProductResponse> products = productService.searchProducts(criteria);
        return ResponseEntity.ok(ApiResponse.ok("Products retrieved successfully", products));
    }

    /**
     * Public endpoint: Get product by ID.
     * GET /api/products/{id}
     */
    @Operation(summary = "Get product",
            description = "PUBLIC: no JWT or role required. Returns record by id; missing record 404.",
            tags = {"Product"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.ok("Product retrieved successfully", product));
    }

    /**
     * Admin endpoint: Create a new product.
     * POST /api/admin/products
     */
    @Operation(summary = "Create product",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Validates request schema, trims name and generates unique slug. Duplicate name 409.",
            tags = {"Product"},
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
    @PostMapping("/admin/products")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created successfully", created));
    }

    /**
     * Admin endpoint: Update an existing product.
     * PUT /api/admin/products/{id}
     */
    @Operation(summary = "Update product",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Replaces editable fields using request schema. Name unique; missing resource/reference 404.",
            tags = {"Product"},
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
    @PutMapping("/admin/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Product updated successfully", updated));
    }

    /**
     * Admin endpoint: Delete a product.
     * DELETE /api/admin/products/{id}
     */
    @Operation(summary = "Delete product",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Deletes by id. HTTP 200 omits data. Database relationships may prevent deletion (500).",
            tags = {"Product"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @DeleteMapping("/admin/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@io.swagger.v3.oas.annotations.Parameter(description = "Database identifier of the resource in this path.") @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted successfully"));
    }
}
