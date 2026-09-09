package com.electronics.store.controller;

import com.electronics.store.dto.request.OrderSearchCriteria;
import com.electronics.store.dto.request.UpdateOrderStatusRequest;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.OrderResponse;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.entity.OrderStatus;
import com.electronics.store.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        return ResponseEntity.ok(ApiResponse.ok("Orders retrieved successfully", orderService.getAllOrders(
                new OrderSearchCriteria(status, userId, keyword, fromDate, toDate), page, size)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Order retrieved successfully",
                orderService.getOrderByIdForAdmin(orderId)));
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(@PathVariable Long orderId,
                                                                  @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Order status updated successfully",
                orderService.updateOrderStatus(orderId, request)));
    }
}
