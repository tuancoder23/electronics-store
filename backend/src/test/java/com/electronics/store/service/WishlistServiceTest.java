package com.electronics.store.service;

import com.electronics.store.entity.*;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.UserMapper;
import com.electronics.store.mapper.WishlistMapper;
import com.electronics.store.repository.*;
import com.electronics.store.service.impl.WishlistServiceImpl;
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
class WishlistServiceTest {
    @Mock WishlistItemRepository wishlist;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock UserService userService;
    @Mock WishlistMapper mapper;
    @InjectMocks WishlistServiceImpl service;

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidProductIdStopsBeforeDatabaseAccess(Long id) {
        assertThatThrownBy(() -> service.addProduct(id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeProduct(id)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(wishlist, products, users, userService);
    }

    @Test
    void duplicateUsesExistingItemWithoutWritingOrChangingItsIdentity() {
        UserEntity user = currentUser();
        ProductEntity product = ProductEntity.builder().id(3L).build();
        WishlistItemEntity original = WishlistItemEntity.builder().id(9L).user(user).product(product).build();
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(wishlist.findByUserIdAndProductId(1L, 3L)).thenReturn(Optional.of(original));
        service.addProduct(3L);
        verify(mapper).toResponse(original);
        verify(wishlist, never()).saveAndFlush(any());
        assertThat(original.getId()).isEqualTo(9L);
    }

    @Test
    void removingItemOutsideCurrentUserScopeNeverDeletesAnything() {
        currentUser();
        service.removeProduct(3L);
        verify(wishlist).findByUserIdAndProductId(1L, 3L);
        verify(wishlist, never()).delete(any());
        verify(wishlist, never()).flush();
        verifyNoInteractions(products);
    }

    @Test
    void deletedCurrentUserCannotReadOrMutateWishlistThroughWritePath() {
        UserEntity user = UserEntity.builder().id(1L).email("deleted@example.test").build();
        when(userService.getCurrentUser()).thenReturn(new UserMapper().toResponse(user));
        assertThatThrownBy(() -> service.addProduct(3L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(wishlist, products, mapper);
    }

    private UserEntity currentUser() {
        UserEntity user = UserEntity.builder().id(1L).email("user@example.test").build();
        when(userService.getCurrentUser()).thenReturn(new UserMapper().toResponse(user));
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        return user;
    }
}
