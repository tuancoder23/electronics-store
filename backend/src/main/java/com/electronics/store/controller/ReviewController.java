package com.electronics.store.controller;

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

    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> getProductReviews(
            @PathVariable Long productId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Reviews retrieved successfully",
                reviewService.getProductReviews(productId, page, size)));
    }

    @GetMapping("/products/{productId}/rating-summary")
    public ResponseEntity<ApiResponse<ProductRatingSummaryResponse>> getProductRatingSummary(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Rating summary retrieved successfully",
                reviewService.getProductRatingSummary(productId)));
    }

    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long productId, @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Review created successfully",
                reviewService.createReview(productId, request)));
    }

    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long reviewId, @Valid @RequestBody UpdateReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Review updated successfully", reviewService.updateReview(reviewId, request)));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Review deleted successfully"));
    }
}
