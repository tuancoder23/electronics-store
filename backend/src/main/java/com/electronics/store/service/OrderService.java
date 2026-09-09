package com.electronics.store.service;

import com.electronics.store.dto.request.CheckoutRequest;
import com.electronics.store.dto.response.OrderResponse;
import com.electronics.store.dto.response.PagedResponse;

public interface OrderService {
    OrderResponse createOrderFromCurrentCart(CheckoutRequest request);
    PagedResponse<OrderResponse> getCurrentUserOrders(int page, int size);
    OrderResponse getCurrentUserOrderById(Long orderId);
}
