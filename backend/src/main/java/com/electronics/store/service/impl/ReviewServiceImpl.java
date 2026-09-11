package com.electronics.store.service.impl;

import com.electronics.store.dto.request.CreateReviewRequest;
import com.electronics.store.dto.request.UpdateReviewRequest;
import com.electronics.store.dto.response.*;
import com.electronics.store.entity.*;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ForbiddenOperationException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.ReviewMapper;
import com.electronics.store.repository.*;
import com.electronics.store.service.ReviewService;
import com.electronics.store.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final OrderItemRepository orderItems;
    private final UserRepository users;
    private final UserService userService;
    private final ReviewMapper mapper;

    @Override
    public PagedResponse<ReviewResponse> getProductReviews(Long productId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        requireProduct(productId);
        return PagedResponse.from(reviews.findByProductIdOrderByCreatedAtDescIdDesc(
                productId, PageRequest.of(page, size)).map(mapper::toResponse));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResponse createReview(Long productId, CreateReviewRequest request) {
        UserEntity user = lockCurrentUser();
        validateId(productId, "Product");
        ProductEntity product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        if (reviews.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new DuplicateResourceException("You have already reviewed this product.");
        }
        if (!orderItems.existsByOrderUserIdAndProductIdAndOrderStatus(user.getId(), productId, OrderStatus.DELIVERED)) {
            if (!orderItems.existsByOrderUserIdAndProductId(user.getId(), productId)) {
                throw new ForbiddenOperationException("You have not purchased this product.");
            }
            throw new ForbiddenOperationException("You can review this product only after your order is delivered.");
        }
        validateReview(request.rating(), request.comment());
        ReviewEntity review = ReviewEntity.builder().user(user).product(product)
                .rating(request.rating()).comment(request.comment().strip()).build();
        return mapper.toResponse(reviews.saveAndFlush(review));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResponse updateReview(Long reviewId, UpdateReviewRequest request) {
        ReviewEntity review = requireOwnedReview(reviewId, lockCurrentUser().getId());
        validateReview(request.rating(), request.comment());
        review.setRating(request.rating());
        review.setComment(request.comment().strip());
        return mapper.toResponse(reviews.saveAndFlush(review));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteReview(Long reviewId) {
        ReviewEntity review = requireOwnedReview(reviewId, lockCurrentUser().getId());
        reviews.delete(review);
        reviews.flush();
    }

    @Override
    public ProductRatingSummaryResponse getProductRatingSummary(Long productId) {
        requireProduct(productId);
        return reviews.getProductRatingSummary(productId);
    }

    private UserEntity lockCurrentUser() {
        // Reuse the existing SecurityContext resolution and user lock used by wishlist writes.
        // Serialize all review writes for this user, including when the review does not exist yet.
        return users.findByEmailForUpdate(userService.getCurrentUser().email())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private ReviewEntity requireOwnedReview(Long reviewId, Long userId) {
        validateId(reviewId, "Review");
        // A scoped lookup returns 404 for both missing and other users' reviews.
        return reviews.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found for current user with id: " + reviewId));
    }

    private void requireProduct(Long productId) {
        validateId(productId, "Product");
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
    }

    private void validateId(Long id, String resource) {
        if (id == null || id < 1) throw new IllegalArgumentException(resource + " ID must be positive");
    }

    private void validateReview(Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (comment == null || comment.isBlank() || comment.strip().length() > 1000) {
            throw new IllegalArgumentException("Comment must be between 1 and 1000 characters");
        }
    }
}
