package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.electronics.store.dto.request.CheckoutRequest;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.OrderResponse;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @Operation(summary = "Checkout current cart",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Atomically creates PENDING order and PENDING payment, snapshots current database prices, deducts stock and clears cart. shippingFee=0. COD available by default; VNPAY needs enabled valid sandbox configuration and supported VND amount. Rejects empty cart/insufficient stock/inactive products and client totals/userId/items. OrderResponse includes payment.",
            tags = {"Checkout", "Payment COD"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Order created successfully",
                orderService.createOrderFromCurrentCart(request)));
    }

    @Operation(summary = "List my orders",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Only current user orders. page >= 0; size 1..100. createdAt DESC then id DESC.",
            tags = {"Order"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getMyOrders(
            @io.swagger.v3.oas.annotations.Parameter(description = "Zero-based page number, minimum 0.") @RequestParam(defaultValue = "0") int page, @io.swagger.v3.oas.annotations.Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Orders retrieved successfully",
                orderService.getCurrentUserOrders(page, size)));
    }

    @Operation(summary = "Get my order",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Returns snapshots and payment. Missing/foreign order both 404, including for ADMIN.",
            tags = {"Order"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@io.swagger.v3.oas.annotations.Parameter(description = "Order identifier; personal APIs require ownership (foreign order returns 404).") @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Order retrieved successfully",
                orderService.getCurrentUserOrderById(orderId)));
    }

    @Operation(summary = "Cancel my pending order",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Only owned PENDING orders; PAID blocks cancellation, refunds unsupported. Restores stock and cancels PENDING payment atomically. Missing product/invalid stock fully rolls back. Repeated cancellation 400. No body.",
            tags = {"User Order Cancellation"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@io.swagger.v3.oas.annotations.Parameter(description = "Order identifier; personal APIs require ownership (foreign order returns 404).") @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled successfully",
                orderService.cancelCurrentUserOrder(orderId)));
    }
}
