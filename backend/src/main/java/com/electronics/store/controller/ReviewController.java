package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.electronics.store.dto.request.CreateReviewRequest;
import com.electronics.store.dto.request.UpdateReviewRequest;
import com.electronics.store.dto.response.*;
import com.electronics.store.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(summary = "List product reviews",
            description = "PUBLIC: no JWT or role required. Positive existing productId. page >= 0, size 1..100. createdAt DESC then id DESC. User summary only id/fullName.",
            tags = {"Review / Rating"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> getProductReviews(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId, @io.swagger.v3.oas.annotations.Parameter(description = "Zero-based page number, minimum 0.") @RequestParam(defaultValue = "0") int page,
            @io.swagger.v3.oas.annotations.Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Reviews retrieved successfully",
                reviewService.getProductReviews(productId, page, size)));
    }

    @Operation(summary = "Get rating summary",
            description = "PUBLIC: no JWT or role required. Positive existing productId. Returns averageRating/reviewCount; both zero for no reviews.",
            tags = {"Review / Rating"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/products/{productId}/rating-summary")
    public ResponseEntity<ApiResponse<ProductRatingSummaryResponse>> getProductRatingSummary(@io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Rating summary retrieved successfully",
                reviewService.getProductRatingSummary(productId)));
    }

    @Operation(summary = "Review purchased product",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Requires DELIVERED order containing product (403 otherwise). One review per user/product (409 duplicate). Rating JSON integer 1..5; stripped comment 1..1000. Unknown fields rejected.",
            tags = {"Review / Rating"},
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
    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @io.swagger.v3.oas.annotations.Parameter(description = "Product identifier; must exist where the operation requires a product.") @PathVariable Long productId, @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Review created successfully",
                reviewService.createReview(productId, request)));
    }

    @Operation(summary = "Update my review",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Owner only; missing/foreign review 404 including ADMIN. Rating JSON integer 1..5; stripped comment 1..1000. Unknown fields rejected.",
            tags = {"Review / Rating"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @io.swagger.v3.oas.annotations.Parameter(description = "Review identifier; owner only, foreign review returns 404.") @PathVariable Long reviewId, @Valid @RequestBody UpdateReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Review updated successfully", reviewService.updateReview(reviewId, request)));
    }

    @Operation(summary = "Delete my review",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Owner only; missing/foreign review 404 including ADMIN. No body; success omits data.",
            tags = {"Review / Rating"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@io.swagger.v3.oas.annotations.Parameter(description = "Review identifier; owner only, foreign review returns 404.") @PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Review deleted successfully"));
    }
}
