package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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

    @Operation(summary = "Search all orders",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Filters combined with AND. Keyword matches receiverName or phone case-insensitively. Inclusive dates; fromDate <= toDate. userId positive. page >= 0, size 1..100. Fixed createdAt DESC then id DESC.",
            tags = {"Admin Order Management"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getAllOrders(
            @io.swagger.v3.oas.annotations.Parameter(description = "Zero-based page number, minimum 0.") @RequestParam(defaultValue = "0") int page,
            @io.swagger.v3.oas.annotations.Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") int size,
            @io.swagger.v3.oas.annotations.Parameter(description = "Optional exact enum name. Omit for all statuses.") @RequestParam(required = false) OrderStatus status,
            @io.swagger.v3.oas.annotations.Parameter(description = "Optional positive user identifier; admin filter across users.") @RequestParam(required = false) Long userId,
            @io.swagger.v3.oas.annotations.Parameter(description = "Case-insensitive substring search; surrounding whitespace is trimmed.") @RequestParam(required = false) String keyword,
            @io.swagger.v3.oas.annotations.Parameter(description = "Inclusive lower createdAt bound, ISO local date-time without offset, e.g. 2026-09-01T00:00:00.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @io.swagger.v3.oas.annotations.Parameter(description = "Inclusive upper createdAt bound, ISO local date-time without offset; must not precede fromDate.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        return ResponseEntity.ok(ApiResponse.ok("Orders retrieved successfully", orderService.getAllOrders(
                new OrderSearchCriteria(status, userId, keyword, fromDate, toDate), page, size)));
    }

    @Operation(summary = "Get any order",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Order/payment across users, no ownership restriction. Missing order 404.",
            tags = {"Admin Order Management"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@io.swagger.v3.oas.annotations.Parameter(description = "Order identifier; personal APIs require ownership (foreign order returns 404).") @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Order retrieved successfully",
                orderService.getOrderByIdForAdmin(orderId)));
    }

    @Operation(summary = "Update order status",
            description = "ROLE_ADMIN: Bearer JWT; ADMIN required. Allowed: PENDING -> CONFIRMED/CANCELLED; CONFIRMED -> SHIPPING/CANCELLED; SHIPPING -> DELIVERED. Other/repeated transitions 400. Cancellation restores stock and cancels PENDING payment; PAID blocks cancellation. COD becomes PAID on DELIVERED. VNPay updated by IPN; no paid-before-shipping gate. Status enum name required, not ordinal; unknown fields rejected.",
            tags = {"Admin Order Management", "Payment COD"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(@io.swagger.v3.oas.annotations.Parameter(description = "Order identifier; personal APIs require ownership (foreign order returns 404).") @PathVariable Long orderId,
                                                                  @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Order status updated successfully",
                orderService.updateOrderStatus(orderId, request)));
    }
}
