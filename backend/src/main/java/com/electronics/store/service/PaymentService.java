package com.electronics.store.service;

// Internal order lifecycle operations. Call within the order transaction, after authorization.
public interface PaymentService {
    void createPaymentForOrder(Long orderId);

    void markCodAsPaid(Long orderId);

    void cancelPendingPayment(Long orderId);
}
