package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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
    @Operation(summary = "List product images",
            description = "PUBLIC: no JWT or role required. Existing product required. Ordered by displayOrder then id.",
            tags = {"Product Images"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/products/{productId}/images")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImagesByProductId(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId
    ) {
        List<ProductImageResponse> images = productImageService.getImagesByProductId(productId);
        return ResponseEntity.ok(ApiResponse.ok("Product images retrieved successfully", images));
    }

    /**
     * Public endpoint: Get product image by ID.
     * GET /api/product-images/{imageId}
     */
    @Operation(summary = "Get image",
            description = "PUBLIC: no JWT or role required. Returns image URL and metadata.",
            tags = {"Product Images"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/product-images/{imageId}")
    public ResponseEntity<ApiResponse<ProductImageResponse>> getImageById(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product image identifier.") @PathVariable Long imageId
    ) {
        ProductImageResponse image = productImageService.getImageById(imageId);
        return ResponseEntity.ok(ApiResponse.ok("Product image retrieved successfully", image));
    }

    /**
     * Admin endpoint: Add a new image to a product.
     * POST /api/admin/products/{productId}/images
     */
    @Operation(summary = "Add image",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Stores URL/metadata, not binary upload. First image becomes primary; primary=true clears previous primary. displayOrder defaults to 0.",
            tags = {"Product Images"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PostMapping("/admin/products/{productId}/images")
    public ResponseEntity<ApiResponse<ProductImageResponse>> addImage(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId,
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
    @Operation(summary = "Update image",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Updates URL and metadata. primary=true selects image and clears previous primary.",
            tags = {"Product Images"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/admin/product-images/{imageId}")
    public ResponseEntity<ApiResponse<ProductImageResponse>> updateImage(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product image identifier.") @PathVariable Long imageId,
            @Valid @RequestBody ProductImageRequest request
    ) {
        ProductImageResponse updated = productImageService.updateImage(imageId, request);
        return ResponseEntity.ok(ApiResponse.ok("Product image updated successfully", updated));
    }

    /**
     * Admin endpoint: Set image as primary.
     * PUT /api/admin/product-images/{imageId}/primary
     */
    @Operation(summary = "Select primary image",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Selects primary and clears previous primary for product. No body.",
            tags = {"Product Images"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/admin/product-images/{imageId}/primary")
    public ResponseEntity<ApiResponse<ProductImageResponse>> setPrimaryImage(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product image identifier.") @PathVariable Long imageId
    ) {
        ProductImageResponse updated = productImageService.setPrimaryImage(imageId);
        return ResponseEntity.ok(ApiResponse.ok("Primary image updated successfully", updated));
    }

    /**
     * Admin endpoint: Delete a product image.
     * DELETE /api/admin/product-images/{imageId}
     */
    @Operation(summary = "Delete image",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Deletes metadata; deleting primary selects another image when available. Does not delete remote file.",
            tags = {"Product Images"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @DeleteMapping("/admin/product-images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product image identifier.") @PathVariable Long imageId
    ) {
        productImageService.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.ok("Product image deleted successfully"));
    }
}
