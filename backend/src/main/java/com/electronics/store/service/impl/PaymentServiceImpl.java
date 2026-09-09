package com.electronics.store.service.impl;

import com.electronics.store.entity.*;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.repository.OrderRepository;
import com.electronics.store.repository.PaymentRepository;
import com.electronics.store.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Override
    public void createPaymentForOrder(Long orderId) {
        OrderEntity order = lockedOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Payment can only be created for a PENDING order");
        }
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new DuplicateResourceException("Payment already exists for order: " + orderId);
        }
        createPendingCodPayment(order);
    }

    @Override
    public void markCodAsPaid(Long orderId) {
        OrderEntity order = lockedOrder(orderId);
        if (order.getPaymentMethod() != PaymentMethod.COD || order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("COD payment can only be marked PAID for a DELIVERED order");
        }
        PaymentEntity payment = paymentForTransition(order);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Only a PENDING COD payment can be marked PAID");
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.saveAndFlush(payment);
    }

    @Override
    public void cancelPendingPayment(Long orderId) {
        OrderEntity order = lockedOrder(orderId);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Payment can only be cancelled for a CANCELLED order");
        }
        PaymentEntity payment = paymentForTransition(order);
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Cannot cancel an order with a PAID payment; refunds are not supported");
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepository.saveAndFlush(payment);
        }
    }

    private OrderEntity lockedOrder(Long orderId) {
        // Serialize payment creation and mutations with checkout and every order status update.
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    private PaymentEntity paymentForTransition(OrderEntity order) {
        // Legacy COD orders have no payment row. Fill it only during a valid lifecycle mutation.
        PaymentEntity payment = paymentRepository.findByOrderId(order.getId())
                .orElseGet(() -> createPendingCodPayment(order));
        if (payment.getMethod() != order.getPaymentMethod()
                || payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Payment does not match order: " + order.getId());
        }
        order.setPayment(payment);
        return payment;
    }

    private PaymentEntity createPendingCodPayment(OrderEntity order) {
        if (order.getPaymentMethod() != PaymentMethod.COD) {
            throw new IllegalArgumentException("Unsupported payment method. Only COD is supported");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() < 0) {
            throw new IllegalArgumentException("Order total amount must be non-negative");
        }
        PaymentEntity payment = PaymentEntity.builder().order(order).method(PaymentMethod.COD)
                .status(PaymentStatus.PENDING).amount(order.getTotalAmount()).build();
        paymentRepository.saveAndFlush(payment);
        order.setPayment(payment);
        return payment;
    }
}
