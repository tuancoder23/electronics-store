package com.electronics.store.service.impl;

import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.dto.response.WishlistItemResponse;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.UserEntity;
import com.electronics.store.entity.WishlistItemEntity;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.WishlistMapper;
import com.electronics.store.repository.ProductRepository;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.repository.WishlistItemRepository;
import com.electronics.store.service.UserService;
import com.electronics.store.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistServiceImpl implements WishlistService {
    private final WishlistItemRepository wishlistItems;
    private final ProductRepository products;
    private final UserRepository users;
    private final UserService userService;
    private final WishlistMapper mapper;

    @Override
    public PagedResponse<WishlistItemResponse> getCurrentUserWishlist(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        return PagedResponse.from(wishlistItems.findByUserIdOrderByCreatedAtDescIdDesc(
                userService.getCurrentUser().id(), PageRequest.of(page, size)).map(mapper::toResponse));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WishlistItemResponse addProduct(Long productId) {
        validateProductId(productId);
        UserEntity user = lockCurrentUser();
        ProductEntity product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        WishlistItemEntity item = wishlistItems.findByUserIdAndProductId(user.getId(), productId)
                .orElseGet(() -> wishlistItems.saveAndFlush(WishlistItemEntity.builder()
                        .user(user).product(product).build()));
        return mapper.toResponse(item);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeProduct(Long productId) {
        validateProductId(productId);
        UserEntity user = lockCurrentUser();
        wishlistItems.findByUserIdAndProductId(user.getId(), productId).ifPresent(item -> {
            wishlistItems.delete(item);
            wishlistItems.flush();
        });
    }

    private UserEntity lockCurrentUser() {
        // Reuse UserService's SecurityContext resolution and the existing user-row lock.
        // Both writes serialize here, even when no wishlist item exists yet.
        return users.findByEmailForUpdate(userService.getCurrentUser().email())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId < 1) {
            throw new IllegalArgumentException("Product ID must be positive");
        }
    }
}
