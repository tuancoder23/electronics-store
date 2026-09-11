package com.electronics.store.service;

import com.electronics.store.dto.request.CreateReviewRequest;
import com.electronics.store.dto.request.UpdateReviewRequest;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.dto.response.ProductRatingSummaryResponse;
import com.electronics.store.dto.response.ReviewResponse;

public interface ReviewService {
    PagedResponse<ReviewResponse> getProductReviews(Long productId, int page, int size);
    ReviewResponse createReview(Long productId, CreateReviewRequest request);
    ReviewResponse updateReview(Long reviewId, UpdateReviewRequest request);
    void deleteReview(Long reviewId);
    ProductRatingSummaryResponse getProductRatingSummary(Long productId);
}
