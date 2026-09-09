package com.electronics.store.service.impl;

import com.electronics.store.dto.request.CheckoutRequest;
import com.electronics.store.dto.request.OrderSearchCriteria;
import com.electronics.store.dto.request.UpdateOrderStatusRequest;
import com.electronics.store.dto.response.OrderResponse;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.entity.*;
import com.electronics.store.exception.ForbiddenOperationException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.OrderMapper;
import com.electronics.store.repository.*;
import com.electronics.store.service.OrderService;
import com.electronics.store.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final PaymentService paymentService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse createOrderFromCurrentCart(CheckoutRequest request) {
        UserEntity user = currentUser();
        if (request.paymentMethod() != PaymentMethod.COD) {
            throw new IllegalArgumentException("Unsupported payment method. Only COD is supported");
        }
        // Shared with cart mutations: a second checkout must see the cleared cart after waiting.
        CartEntity cart = cartRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty."));
        List<CartItemEntity> cartItems = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty.");
        }

        OrderEntity order = OrderEntity.builder().user(user).receiverName(request.receiverName())
                .phone(request.phone()).shippingAddress(request.shippingAddress()).note(request.note())
                .status(OrderStatus.PENDING).paymentMethod(request.paymentMethod()).build();
        BigDecimal subtotal = BigDecimal.ZERO;

        // Only read proxy IDs here. Load live product state under a write lock in stable ID order.
        cartItems.sort(Comparator.comparing(item -> item.getProduct().getId()));
        for (CartItemEntity cartItem : cartItems) {
            Long productId = cartItem.getProduct().getId();
            ProductEntity product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
            validateStock(product, cartItem.getQuantity());
            BigDecimal unitPrice = effectivePrice(product);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            order.addItem(OrderItemEntity.builder().productId(productId).productName(product.getName())
                    .unitPrice(unitPrice).quantity(cartItem.getQuantity()).lineTotal(lineTotal).build());
            subtotal = subtotal.add(lineTotal);

            product.setQuantity(product.getQuantity() - cartItem.getQuantity());
            if (product.getQuantity() == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            }
            productRepository.save(product);
        }
        order.setSubtotal(subtotal);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(subtotal.add(order.getShippingFee()));
        orderRepository.saveAndFlush(order);
        paymentService.createPaymentForOrder(order.getId());
        cartItemRepository.deleteAll(cartItems);
        cart.touch();
        cartRepository.save(cart);
        // Flush inside the transaction so any persistence failure rolls back every change.
        cartItemRepository.flush();
        return orderMapper.toResponse(order);
    }

    @Override
    public PagedResponse<OrderResponse> getCurrentUserOrders(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        return PagedResponse.from(orderRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                currentUser().getId(), PageRequest.of(page, size)).map(orderMapper::toResponse));
    }

    @Override
    public OrderResponse getCurrentUserOrderById(Long orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, currentUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return orderMapper.toResponse(order);
    }

    @Override
    public PagedResponse<OrderResponse> getAllOrders(OrderSearchCriteria criteria, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        if (criteria.userId() != null && criteria.userId() < 1) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        if (criteria.fromDate() != null && criteria.toDate() != null
                && criteria.fromDate().isAfter(criteria.toDate())) {
            throw new IllegalArgumentException("From date must not be after to date");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PagedResponse.from(orderRepository.findAll(OrderSpecification.withCriteria(criteria), pageable)
                .map(orderMapper::toResponse));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse cancelCurrentUserOrder(Long orderId) {
        // Ownership and the lock use one query; share the order row lock with admin updates.
        OrderEntity order = orderRepository.findByIdAndUserIdForUpdate(orderId, currentUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be cancelled by the user");
        }
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        paymentService.cancelPendingPayment(order.getId());
        orderRepository.saveAndFlush(order);
        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponse getOrderByIdForAdmin(Long orderId) {
        return orderMapper.toResponse(orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId)));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        if (request.status() == null) {
            throw new IllegalArgumentException("Order status is required");
        }
        // Read status only after acquiring the lock: concurrent updates cannot validate stale state.
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        validateTransition(order.getStatus(), request.status());
        if (request.status() == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        order.setStatus(request.status());
        if (request.status() == OrderStatus.CANCELLED) {
            paymentService.cancelPendingPayment(order.getId());
        } else if (request.status() == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            paymentService.markCodAsPaid(order.getId());
        }
        // Flush stock, order and payment together; any failure rolls the entire transaction back.
        orderRepository.saveAndFlush(order);
        return orderMapper.toResponse(order);
    }

    private void validateTransition(OrderStatus current, OrderStatus desired) {
        boolean allowed = switch (current) {
            case PENDING -> desired == OrderStatus.CONFIRMED || desired == OrderStatus.CANCELLED;
            case CONFIRMED -> desired == OrderStatus.SHIPPING || desired == OrderStatus.CANCELLED;
            case SHIPPING -> desired == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Cannot change order status from " + current + " to " + desired);
        }
    }

    private void restoreStock(OrderEntity order) {
        // Match checkout's product lock order to avoid deadlocks and lost stock updates.
        List<OrderItemEntity> items = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItemEntity::getProductId)).toList();
        for (OrderItemEntity item : items) {
            ProductEntity product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot cancel order: product no longer exists with id: " + item.getProductId()));
            Integer stock = product.getQuantity();
            Integer quantity = item.getQuantity();
            if (stock == null || stock < 0 || quantity == null || quantity < 1
                    || stock > Integer.MAX_VALUE - quantity) {
                throw new IllegalArgumentException("Cannot restore stock for product with id: " + item.getProductId());
            }
            product.setQuantity(stock + quantity);
            if (product.getStatus() == ProductStatus.OUT_OF_STOCK && product.getQuantity() > 0) {
                product.setStatus(ProductStatus.ACTIVE);
            }
            productRepository.save(product);
        }
    }

    private UserEntity currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ForbiddenOperationException("Authentication is required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private void validateStock(ProductEntity product, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new IllegalArgumentException("Product is not available for checkout: " + product.getName());
        }
        if (product.getQuantity() == null || product.getQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
        }
    }

    private BigDecimal effectivePrice(ProductEntity product) {
        BigDecimal price = product.getPrice();
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Product price is invalid: " + product.getName());
        }
        BigDecimal discount = product.getDiscountPrice();
        return discount != null && discount.signum() >= 0 && discount.compareTo(price) < 0 ? discount : price;
    }
}
