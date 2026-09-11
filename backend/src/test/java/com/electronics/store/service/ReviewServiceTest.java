package com.electronics.store.service;

import com.electronics.store.dto.request.UpdateReviewRequest;
import com.electronics.store.dto.request.CreateReviewRequest;
import com.electronics.store.entity.*;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.ReviewMapper;
import com.electronics.store.mapper.UserMapper;
import com.electronics.store.repository.*;
import com.electronics.store.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock ReviewRepository reviews;
    @Mock ProductRepository products;
    @Mock OrderItemRepository orderItems;
    @Mock UserRepository users;
    @Mock UserService userService;
    @Mock ReviewMapper mapper;
    @InjectMocks ReviewServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void currentUser() {
        user = UserEntity.builder().id(1L).email("user@example.test").build();
        when(userService.getCurrentUser()).thenReturn(new UserMapper().toResponse(user));
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 6, Integer.MAX_VALUE})
    void directServiceCallRejectsInvalidRatingWithoutMutatingExistingReview(Integer rating) {
        ReviewEntity original = ownedReview();
        assertThatThrownBy(() -> service.updateReview(5L, new UpdateReviewRequest(rating, "Changed")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Rating");
        unchanged(original);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \t\n", "too-long"})
    void directServiceCallRejectsInvalidCommentWithoutChangingRating(String value) {
        ReviewEntity original = ownedReview();
        String comment = "too-long".equals(value) ? "x".repeat(1001) : value;
        assertThatThrownBy(() -> service.updateReview(5L, new UpdateReviewRequest(1, comment)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Comment");
        unchanged(original);
    }

    @Test
    void nonOwnerCannotUpdateOrDeleteAndNeverTriggersUnscopedLookup() {
        assertThatThrownBy(() -> service.updateReview(5L, new UpdateReviewRequest(5, "Changed")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteReview(5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(reviews, times(2)).findByIdAndUserId(5L, 1L);
        verify(reviews, never()).findById(any());
        verify(reviews, never()).saveAndFlush(any());
        verify(reviews, never()).delete(any());
    }

    @Test
    void duplicateStopsBeforePurchaseQueriesOrPersistence() {
        when(products.findById(3L)).thenReturn(Optional.of(ProductEntity.builder().id(3L).build()));
        when(reviews.existsByUserIdAndProductId(1L, 3L)).thenReturn(true);
        assertThatThrownBy(() -> service.createReview(3L, new CreateReviewRequest(5, "Good")))
                .isInstanceOf(DuplicateResourceException.class);
        verifyNoInteractions(orderItems, mapper);
        verify(reviews, never()).saveAndFlush(any());
    }

    private ReviewEntity ownedReview() {
        ReviewEntity original = ReviewEntity.builder().id(5L).user(user).rating(4).comment("Original").build();
        when(reviews.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(original));
        return original;
    }

    private void unchanged(ReviewEntity original) {
        assertThat(original.getRating()).isEqualTo(4);
        assertThat(original.getComment()).isEqualTo("Original");
        verify(reviews, never()).saveAndFlush(any());
        verifyNoInteractions(mapper);
    }
}
