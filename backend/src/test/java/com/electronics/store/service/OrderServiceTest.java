package com.electronics.store.service;

import com.electronics.store.config.VnPayProperties;
import com.electronics.store.dto.request.CheckoutRequest;
import com.electronics.store.dto.request.UpdateOrderStatusRequest;
import com.electronics.store.entity.*;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.OrderMapper;
import com.electronics.store.repository.*;
import com.electronics.store.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orders;
    @Mock CartRepository carts;
    @Mock CartItemRepository cartItems;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock OrderMapper mapper;
    @Mock PaymentService payments;
    @Mock VnPayProperties vnpay;
    @InjectMocks OrderServiceImpl service;

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void skippingShippingCannotReachPaymentOrInventoryWrites() {
        OrderEntity order = OrderEntity.builder().id(5L).status(OrderStatus.CONFIRMED).build();
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.updateOrderStatus(5L, new UpdateOrderStatusRequest(OrderStatus.DELIVERED)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CONFIRMED to DELIVERED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(products, payments, mapper);
    }

    @Test
    void nonOwnerCancellationStopsBeforeStockAndPaymentWork() {
        currentUser();
        assertThatThrownBy(() -> service.cancelCurrentUserOrder(5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(orders).findByIdAndUserIdForUpdate(5L, 1L);
        verify(orders, never()).findByIdForUpdate(any());
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(products, payments, mapper);
    }

    @Test
    void unavailableVnpayFailsBeforeLockingCartOrDecrementingStock() {
        currentUser();
        doThrow(new IllegalArgumentException("VNPAY sandbox is disabled")).when(vnpay).requireConfigured();
        assertThatThrownBy(() -> service.createOrderFromCurrentCart(
                new CheckoutRequest("Customer", "0901234567", "Test street", null, PaymentMethod.VNPAY)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
        verifyNoInteractions(carts, cartItems, products, orders, payments, mapper);
    }

    private void currentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.test", null, List.of()));
        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(
                UserEntity.builder().id(1L).email("user@example.test").build()));
    }
}
