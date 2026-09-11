package com.electronics.store.service;

import com.electronics.store.dto.request.AddCartItemRequest;
import com.electronics.store.dto.request.UpdateCartItemRequest;
import com.electronics.store.entity.*;
import com.electronics.store.exception.ForbiddenOperationException;
import com.electronics.store.mapper.CartItemMapper;
import com.electronics.store.mapper.CartMapper;
import com.electronics.store.repository.*;
import com.electronics.store.service.impl.CartServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @Mock CartRepository carts;
    @Mock CartItemRepository items;
    @Mock ProductRepository products;
    @Mock UserRepository users;

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private CartServiceImpl service() {
        return new CartServiceImpl(carts, items, products, users, new CartMapper(), new CartItemMapper());
    }

    @Test
    void displayRecalculatesExactPricesWithoutSavingOrReservingStock() {
        CartEntity cart = currentCart();
        ProductEntity product = ProductEntity.builder().id(3L).price(new BigDecimal("100.10"))
                .discountPrice(new BigDecimal("80.05")).quantity(10).build();
        when(items.findForDisplayByCartId(2L)).thenReturn(List.of(
                CartItemEntity.builder().id(4L).cart(cart).product(product).quantity(3).build()));
        var response = service().getCurrentUserCart();
        assertThat(response.subtotal()).isEqualByComparingTo("240.15");
        assertThat(response.totalItems()).isEqualTo(3);
        product.setDiscountPrice(null);
        assertThat(service().getCurrentUserCart().subtotal()).isEqualByComparingTo("300.30");
        assertThat(product.getQuantity()).isEqualTo(10);
        verify(items, never()).save(any());
        verify(carts, never()).save(any());
        verifyNoInteractions(products);
    }

    @Test
    void overflowingAdditionFailsBeforeMutatingOrSavingExistingItem() {
        CartEntity cart = currentCart();
        ProductEntity product = ProductEntity.builder().id(3L).quantity(Integer.MAX_VALUE).build();
        CartItemEntity item = CartItemEntity.builder().cart(cart).product(product).quantity(Integer.MAX_VALUE).build();
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(items.findByCartIdAndProductId(2L, 3L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service().addItem(new AddCartItemRequest(3L, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Insufficient stock");
        assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);
        verify(items, never()).save(any());
        verify(carts, never()).save(any());
    }

    @Test
    void ownershipFailureNeverChangesOrDeletesForeignItem() {
        currentCart();
        CartItemEntity foreign = CartItemEntity.builder().id(4L)
                .cart(CartEntity.builder().id(99L).build()).quantity(2).build();
        when(items.findById(4L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service().updateItem(4L, new UpdateCartItemRequest(1)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service().removeItem(4L)).isInstanceOf(ForbiddenOperationException.class);
        assertThat(foreign.getQuantity()).isEqualTo(2);
        verify(items, never()).save(any());
        verify(items, never()).delete(any());
        verifyNoInteractions(products);
    }

    private CartEntity currentCart() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.test", null, List.of()));
        UserEntity user = UserEntity.builder().id(1L).email("user@example.test").build();
        CartEntity cart = CartEntity.builder().id(2L).user(user).build();
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(carts.findByUserIdForUpdate(1L)).thenReturn(Optional.of(cart));
        return cart;
    }
}
