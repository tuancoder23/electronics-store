package com.electronics.store.service.impl;

import com.electronics.store.dto.request.AddCartItemRequest;
import com.electronics.store.dto.request.UpdateCartItemRequest;
import com.electronics.store.dto.response.CartResponse;
import com.electronics.store.dto.response.CartItemResponse;
import com.electronics.store.entity.*;
import com.electronics.store.exception.ForbiddenOperationException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.CartMapper;
import com.electronics.store.mapper.CartItemMapper;
import com.electronics.store.repository.CartItemRepository;
import com.electronics.store.repository.CartRepository;
import com.electronics.store.repository.ProductRepository;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.service.CartService;
import com.electronics.store.util.ProductPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse getCurrentUserCart() {
        return response(getOrCreateCart(currentUser()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse addItem(AddCartItemRequest request) {
        CartEntity cart = getOrCreateCart(currentUser());
        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.productId()));
        validatePurchasable(product);

        CartItemEntity item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElseGet(() -> CartItemEntity.builder().cart(cart).product(product).quantity(0).build());
        long newQuantity = (long) item.getQuantity() + request.quantity();
        validateStock(product, newQuantity);
        item.setQuantity((int) newQuantity);
        cartItemRepository.save(item);
        touch(cart);
        return response(cart);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse updateItem(Long cartItemId, UpdateCartItemRequest request) {
        CartEntity cart = getOrCreateCart(currentUser());
        CartItemEntity item = ownedItem(cartItemId, cart);
        validatePurchasable(item.getProduct());
        validateStock(item.getProduct(), request.quantity());
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        touch(cart);
        return response(cart);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse removeItem(Long cartItemId) {
        CartEntity cart = getOrCreateCart(currentUser());
        CartItemEntity item = ownedItem(cartItemId, cart);
        cartItemRepository.delete(item);
        cartItemRepository.flush();
        touch(cart);
        return response(cart);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartResponse clearCart() {
        CartEntity cart = getOrCreateCart(currentUser());
        cartItemRepository.deleteByCartId(cart.getId());
        cartItemRepository.flush();
        touch(cart);
        return response(cart);
    }

    private UserEntity currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ForbiddenOperationException("Authentication is required");
        }
        // Lock an existing row even before the user's first cart exists, then lock the cart.
        return userRepository.findByEmailForUpdate(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private CartEntity getOrCreateCart(UserEntity user) {
        return cartRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> cartRepository.save(CartEntity.builder().user(user).build()));
    }

    private CartItemEntity ownedItem(Long itemId, CartEntity cart) {
        CartItemEntity item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new ForbiddenOperationException("You cannot access another user's cart item");
        }
        return item;
    }

    private void validatePurchasable(ProductEntity product) {
        if (product.getStatus() == ProductStatus.INACTIVE) {
            throw new IllegalArgumentException("Product is inactive and cannot be added to cart");
        }
        if (product.getStatus() == ProductStatus.OUT_OF_STOCK || product.getQuantity() == null || product.getQuantity() < 1) {
            throw new IllegalArgumentException("Product is out of stock");
        }
    }

    private void validateStock(ProductEntity product, long quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (quantity > product.getQuantity()) {
            throw new IllegalArgumentException("Insufficient stock for product: " + product.getName()
                    + ". Available: " + product.getQuantity());
        }
    }

    private void touch(CartEntity cart) {
        cart.touch();
        cartRepository.save(cart);
    }

    private CartResponse response(CartEntity cart) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        long totalItems = 0;
        for (CartItemEntity item : cartItemRepository.findForDisplayByCartId(cart.getId())) {
            BigDecimal price = ProductPricing.effectivePrice(item.getProduct());
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));
            items.add(cartItemMapper.toResponse(item, price, lineTotal));
            subtotal = subtotal.add(lineTotal);
            totalItems += item.getQuantity();
        }
        if (totalItems > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Total cart quantity exceeds the supported limit");
        }
        return cartMapper.toResponse(cart, items, (int) totalItems, subtotal);
    }
}
