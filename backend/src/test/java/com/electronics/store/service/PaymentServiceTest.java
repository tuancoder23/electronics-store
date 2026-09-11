package com.electronics.store.service;

import com.electronics.store.config.VnPayProperties;
import com.electronics.store.entity.*;
import com.electronics.store.repository.OrderRepository;
import com.electronics.store.repository.PaymentRepository;
import com.electronics.store.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PaymentRepository payments;
    @Mock OrderRepository orders;
    @Mock VnPayProperties vnpay;
    @InjectMocks PaymentServiceImpl service;

    @ParameterizedTest
    @ValueSource(strings = {"amount", "method"})
    void mismatchedPaymentIsRejectedBeforeChangingStatusOrWriting(String mismatch) {
        OrderEntity order = order(OrderStatus.DELIVERED);
        PaymentEntity payment = payment(order, PaymentStatus.PENDING);
        if (mismatch.equals("amount")) payment.setAmount(new BigDecimal("99.99"));
        else payment.setMethod(PaymentMethod.VNPAY);
        when(payments.findByOrderId(1L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> service.markCodAsPaid(1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not match order");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaidAt()).isNull();
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void alreadyPaidCodIsIdempotentEvenWhenAmountScaleDiffers() {
        OrderEntity order = order(OrderStatus.DELIVERED);
        PaymentEntity payment = payment(order, PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("100.000"));
        LocalDateTime paidAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        payment.setPaidAt(paidAt);
        when(payments.findByOrderId(1L)).thenReturn(Optional.of(payment));
        service.markCodAsPaid(1L);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
        verify(payments, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELLED"})
    void cancellingTerminalUnpaidPaymentDoesNotWriteAgain(PaymentStatus state) {
        OrderEntity order = order(OrderStatus.CANCELLED);
        PaymentEntity payment = payment(order, state);
        when(payments.findByOrderId(1L)).thenReturn(Optional.of(payment));
        service.cancelPendingPayment(1L);
        assertThat(payment.getStatus()).isEqualTo(state);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void missingVnpayPaymentNeverCreatesLegacyCodFallback() {
        OrderEntity order = order(OrderStatus.CANCELLED);
        order.setPaymentMethod(PaymentMethod.VNPAY);
        assertThatThrownBy(() -> service.cancelPendingPayment(1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("VNPAY payment is missing");
        verify(payments, never()).saveAndFlush(any());
    }

    private OrderEntity order(OrderStatus status) {
        OrderEntity order = OrderEntity.builder().id(1L).status(status).paymentMethod(PaymentMethod.COD)
                .totalAmount(new BigDecimal("100.00")).build();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        return order;
    }

    private PaymentEntity payment(OrderEntity order, PaymentStatus status) {
        return PaymentEntity.builder().order(order).method(PaymentMethod.COD)
                .amount(order.getTotalAmount()).status(status).build();
    }
}
