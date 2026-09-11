package com.electronics.store.order;

import com.electronics.store.entity.*;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.repository.*;
import com.electronics.store.security.CustomUserDetails;
import com.electronics.store.security.JwtService;
import com.electronics.store.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// HTTP/JWT, database constraints and real commits; no test-level transaction masks rollback failures.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false"
})
class PaymentIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired PaymentRepository payments;
    @Autowired PaymentService paymentService;
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate jdbc;

    private ProductEntity productA;
    private ProductEntity productB;
    private String tokenA;
    private String tokenB;
    private String adminToken;

    @BeforeEach
    void setUp() {
        payments.deleteAll();
        orderItems.deleteAll();
        orders.deleteAll();
        cartItems.deleteAll();
        carts.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        brands.deleteAll();
        users.deleteAll();
        tokenA = token("a@example.com", Role.USER);
        tokenB = token("b@example.com", Role.USER);
        adminToken = token("admin@example.com", Role.ADMIN);
        CategoryEntity category = categories.save(CategoryEntity.builder().name("Phones").slug("phones").build());
        BrandEntity brand = brands.save(BrandEntity.builder().name("Acme").slug("acme").build());
        productA = products.save(product("Product A", "product-a", category, brand));
        productB = products.save(product("Product B", "product-b", category, brand));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23990000", "0", "123.45"})
    void codCheckoutPersistsOnePendingPaymentUsingExactOrderAmount(String price) throws Exception {
        productA.setPrice(new BigDecimal(price));
        products.saveAndFlush(productA);
        add(tokenA, productA, 1);
        JsonNode order = data(checkout(tokenA), 201);
        long id = order.path("id").asLong();
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("paymentMethod").asText()).isEqualTo("COD");
        JsonNode response = order.path("payment");
        assertThat(response.path("id").asLong()).isPositive();
        assertThat(response.path("method").asText()).isEqualTo("COD");
        assertThat(response.path("status").asText()).isEqualTo("PENDING");
        assertThat(response.path("amount").decimalValue()).isEqualByComparingTo(price);
        assertThat(response.path("amount").decimalValue())
                .isEqualByComparingTo(order.path("totalAmount").decimalValue());
        assertThat(response.path("transactionCode").isNull()).isTrue();
        assertThat(response.path("paidAt").isNull()).isTrue();
        assertThat(response.path("createdAt").asText()).isNotBlank();
        assertThat(response.path("updatedAt").asText()).isNotBlank();
        PaymentEntity stored = payment(id);
        assertThat(stored.getMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(stored.getAmount()).isEqualByComparingTo(price);
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(stored.getTransactionCode()).isNull();
        assertThat(stored.getPaidAt()).isNull();
        assertThat(payments.existsByOrderId(id)).isTrue();
        assertThat(payments.count()).isEqualTo(1);
        assertThat(stock(productA)).isEqualTo(9);
        assertThat(cartItems.count()).isZero();
        assertThat(jdbc.queryForObject("select method from payments where order_id=?", String.class, id)).isEqualTo("COD");
        assertThat(jdbc.queryForObject("select status from payments where order_id=?", String.class, id)).isEqualTo("PENDING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "payment", "paymentStatus", "paidAt", "transactionCode", "userId"})
    void checkoutRejectsClientControlledPaymentFields(String field) throws Exception {
        add(tokenA, productA, 2);
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(checkoutBody());
        body.put(field, "PAID");
        error(call(HttpMethod.POST, "/api/orders", tokenA, body.toString()), 400);
        assertCheckoutRolledBack(1, 2);
    }

    @Test
    void vnpayCheckoutRemainsDisabledWithoutSandboxConfiguration() throws Exception {
        assertThat(json.readValue("\"VNPAY\"", PaymentMethod.class)).isEqualTo(PaymentMethod.VNPAY);
        add(tokenA, productA, 2);
        ResponseEntity<String> response = call(HttpMethod.POST, "/api/orders", tokenA,
                checkoutBody().replace("COD", "VNPAY"));
        error(response, 400);
        assertThat(json.readTree(response.getBody()).path("message").asText()).contains("VNPAY sandbox");
        assertCheckoutRolledBack(1, 2);
    }

    @Test
    void deliveredCodIsPaidOnlyAfterShippingAndPaidAtIsNotRewritten() throws Exception {
        long id = createOrder(2);
        for (String next : List.of("CONFIRMED", "SHIPPING")) {
            JsonNode order = data(adminUpdate(id, next), 200);
            assertThat(order.path("payment").path("status").asText()).isEqualTo("PENDING");
            assertThat(payment(id).getPaidAt()).isNull();
        }
        JsonNode delivered = data(adminUpdate(id, "DELIVERED"), 200);
        assertThat(delivered.path("payment").path("status").asText()).isEqualTo("PAID");
        PaymentEntity paid = payment(id);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paid.getTransactionCode()).isNull();
        transaction.executeWithoutResult(tx -> paymentService.markCodAsPaid(id));
        error(adminUpdate(id, "DELIVERED"), 400);
        error(cancel(id), 400);
        error(adminUpdate(id, "CANCELLED"), 400);
        assertThat(payment(id).getPaidAt()).isEqualTo(paid.getPaidAt());
        assertThat(payment(id).getUpdatedAt()).isEqualTo(paid.getUpdatedAt());
        assertThat(status(id)).isEqualTo(OrderStatus.DELIVERED);
        assertThat(stock(productA)).isEqualTo(8);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    void paymentServiceRejectsMarkPaidBeforeDelivery(OrderStatus state) throws Exception {
        long id = createOrder(2);
        jdbc.update("update orders set status=? where id=?", state.name(), id);
        assertThrows(IllegalArgumentException.class,
                () -> transaction.executeWithoutResult(tx -> paymentService.markCodAsPaid(id)));
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment(id).getPaidAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin-pending", "admin-confirmed"})
    void cancellationUpdatesPaymentAndRestoresStockExactlyOnce(String actor) throws Exception {
        long id = twoProductOrder();
        if (actor.equals("admin-confirmed")) data(adminUpdate(id, "CONFIRMED"), 200);
        JsonNode result = data(actor.equals("user") ? cancel(id) : adminUpdate(id, "CANCELLED"), 200);
        assertThat(result.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(result.path("payment").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment(id).getPaidAt()).isNull();
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        LocalDateTime before = payment(id).getUpdatedAt();
        error(cancel(id), 400);
        error(adminUpdate(id, "CANCELLED"), 400);
        assertThat(payment(id).getUpdatedAt()).isEqualTo(before);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        assertThat(payments.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin"})
    void paidPaymentBlocksCancellationWithoutRefundOrStockChanges(String actor) throws Exception {
        long id = twoProductOrder();
        PaymentEntity paid = payment(id);
        paid.setStatus(PaymentStatus.PAID);
        paid.setPaidAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        payments.saveAndFlush(paid);
        ResponseEntity<String> response = actor.equals("user") ? cancel(id) : adminUpdate(id, "CANCELLED");
        error(response, 400);
        assertThat(json.readTree(response.getBody()).path("message").asText()).contains("PAID");
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment(id).getPaidAt()).isEqualTo(paid.getPaidAt());
        assertThat(stock(productA)).isEqualTo(8);
        assertThat(stock(productB)).isEqualTo(7);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELLED"})
    void deliveryRejectsFailedOrCancelledPayment(PaymentStatus state) throws Exception {
        long id = shippingOrder();
        jdbc.update("update payments set status=? where order_id=?", state.name(), id);
        error(adminUpdate(id, "DELIVERED"), 400);
        assertThat(status(id)).isEqualTo(OrderStatus.SHIPPING);
        assertThat(payment(id).getStatus()).isEqualTo(state);
        assertThat(payment(id).getPaidAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELLED"})
    void cancellationPreservesNonPendingUnpaidPaymentStatus(PaymentStatus state) throws Exception {
        long id = createOrder(2);
        jdbc.update("update payments set status=? where order_id=?", state.name(), id);
        data(cancel(id), 200);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment(id).getStatus()).isEqualTo(state);
        assertThat(stock(productA)).isEqualTo(10);
    }

    @Test
    void duplicatePaymentIsRejectedByServiceAndDatabase() throws Exception {
        long id = createOrder(2);
        Long original = payment(id).getId();
        assertThrows(DuplicateResourceException.class,
                () -> transaction.executeWithoutResult(tx -> paymentService.createPaymentForOrder(id)));
        PaymentEntity duplicate = PaymentEntity.builder().order(orders.findById(id).orElseThrow())
                .method(PaymentMethod.COD).status(PaymentStatus.PENDING).amount(payment(id).getAmount()).build();
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(duplicate));
        assertThat(payments.count()).isEqualTo(1);
        assertThat(payment(id).getId()).isEqualTo(original);
    }

    @Test
    void paymentMutationsRequireAnExistingTransaction() throws Exception {
        long id = createOrder(2);
        assertThrows(IllegalTransactionStateException.class, () -> paymentService.createPaymentForOrder(id));
        assertThrows(IllegalTransactionStateException.class, () -> paymentService.markCodAsPaid(id));
        assertThrows(IllegalTransactionStateException.class, () -> paymentService.cancelPendingPayment(id));
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void paymentCreationDatabaseFailureRollsBackEntireCheckout() throws Exception {
        add(tokenA, productA, 2);
        add(tokenA, productB, 3);
        jdbc.execute("alter table payments add constraint test_payment_insert_failure check (amount < 0)");
        try {
            error(checkout(tokenA), 500);
            assertCheckoutRolledBack(2, 5);
        } finally {
            jdbc.execute("alter table payments drop constraint test_payment_insert_failure");
        }
        data(checkout(tokenA), 201);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(cartItems.count()).isZero();
    }

    @Test
    void failureAfterPaymentInsertAlsoRollsBackPaymentAndCartClear() throws Exception {
        add(tokenA, productA, 2);
        // Use a historical timestamp: two real-time writes can share the same Windows clock tick.
        jdbc.update("update carts set updated_at=? where id=?", LocalDateTime.of(2000, 1, 1, 0, 0),
                carts.findAll().get(0).getId());
        // The existing cart row is valid; checkout's final touch/flush violates this constraint.
        jdbc.execute("alter table carts add constraint test_cart_touch_failure check (updated_at <= timestamp '"
                + carts.findAll().get(0).getUpdatedAt() + "')");
        try {
            error(checkout(tokenA), 500);
            assertCheckoutRolledBack(1, 2);
        } finally {
            jdbc.execute("alter table carts drop constraint test_cart_touch_failure");
        }
    }

    @Test
    void paymentPaidDatabaseFailureRollsBackDeliveryAndCanRetry() throws Exception {
        long id = shippingOrder();
        jdbc.execute("alter table payments add constraint test_payment_paid_failure check (status <> 'PAID')");
        try {
            error(adminUpdate(id, "DELIVERED"), 500);
            assertThat(status(id)).isEqualTo(OrderStatus.SHIPPING);
            assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment(id).getPaidAt()).isNull();
            assertThat(stock(productA)).isEqualTo(8);
        } finally {
            jdbc.execute("alter table payments drop constraint test_payment_paid_failure");
        }
        data(adminUpdate(id, "DELIVERED"), 200);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin"})
    void paymentCancelDatabaseFailureRollsBackOrderAndRestoredStock(String actor) throws Exception {
        long id = twoProductOrder();
        jdbc.execute("alter table payments add constraint test_payment_cancel_failure check (status <> 'CANCELLED')");
        try {
            error(actor.equals("user") ? cancel(id) : adminUpdate(id, "CANCELLED"), 500);
            assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
            assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(stock(productA)).isEqualTo(8);
            assertThat(stock(productB)).isEqualTo(7);
        } finally {
            jdbc.execute("alter table payments drop constraint test_payment_cancel_failure");
        }
        data(cancel(id), 200);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void stockRestoreFailureKeepsPaymentPending() throws Exception {
        long id = twoProductOrder();
        products.deleteById(productB.getId());
        error(cancel(id), 400);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
    }

    @Test
    void paymentDetailsFollowOrderOwnershipAndAdminRole() throws Exception {
        long id = createOrder(2);
        for (String path : List.of("/api/orders/" + id, "/api/orders/my-orders", "/api/admin/orders/" + id)) {
            error(call(HttpMethod.GET, path, null, null), 401);
            error(call(HttpMethod.GET, path, "invalid", null), 401);
        }
        error(call(HttpMethod.GET, "/api/orders/" + id, tokenB, null), 404);
        error(call(HttpMethod.GET, "/api/admin/orders/" + id, tokenB, null), 403);
        assertThat(data(call(HttpMethod.GET, "/api/orders/my-orders", tokenB, null), 200).path("content")).isEmpty();
        for (String path : List.of("/api/orders/" + id, "/api/admin/orders/" + id)) {
            JsonNode detail = data(call(HttpMethod.GET, path, path.contains("admin") ? adminToken : tokenA, null), 200);
            assertThat(detail.path("payment").path("id").asLong()).isEqualTo(payment(id).getId());
            for (String field : List.of("user", "order", "password", "secret", "accessToken", "authorities")) {
                assertThat(detail.findValues(field)).isEmpty();
            }
        }
        assertThat(data(call(HttpMethod.GET, "/api/orders/my-orders", tokenA, null), 200)
                .path("content").get(0).path("payment").path("status").asText()).isEqualTo("PENDING");
        assertThat(data(call(HttpMethod.GET, "/api/admin/orders", adminToken, null), 200)
                .path("content").get(0).path("payment").path("status").asText()).isEqualTo("PENDING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-cancel", "admin-cancel", "delivered"})
    void legacyCodOrdersRemainReadableAndGainPaymentDuringValidTransition(String transition) throws Exception {
        long id = transition.equals("delivered") ? shippingOrder() : createOrder(2);
        jdbc.update("delete from payments where order_id=?", id);
        JsonNode legacy = data(call(HttpMethod.GET, "/api/orders/" + id, tokenA, null), 200);
        assertThat(legacy.path("payment").isNull()).isTrue();
        assertThat(payments.count()).isZero();
        if (transition.equals("user-cancel")) data(cancel(id), 200);
        else data(adminUpdate(id, transition.equals("delivered") ? "DELIVERED" : "CANCELLED"), 200);
        assertThat(payment(id).getStatus()).isEqualTo(transition.equals("delivered")
                ? PaymentStatus.PAID : PaymentStatus.CANCELLED);
        assertThat(payment(id).getAmount()).isEqualByComparingTo("200000");
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void simultaneousPaymentCreationIsSerializedByOrderLock() throws Exception {
        long id = createOrder(2);
        jdbc.update("delete from payments where order_id=?", id);
        assertThat(concurrent(id, () -> createPayment(id), () -> createPayment(id)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void simultaneousUserAndAdminCancelKeepPaymentAndStockConsistent() throws Exception {
        long id = createOrder(2);
        assertThat(concurrent(id, () -> cancel(id).getStatusCode().value(),
                () -> adminUpdate(id, "CANCELLED").getStatusCode().value())).containsExactlyInAnyOrder(200, 400);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void simultaneousDeliveriesMarkPaymentPaidOnce() throws Exception {
        long id = shippingOrder();
        assertThat(concurrent(id, () -> adminUpdate(id, "DELIVERED").getStatusCode().value(),
                () -> adminUpdate(id, "DELIVERED").getStatusCode().value())).containsExactlyInAnyOrder(200, 400);
        assertThat(status(id)).isEqualTo(OrderStatus.DELIVERED);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment(id).getPaidAt()).isNotNull();
        assertThat(payments.count()).isEqualTo(1);
        assertThat(stock(productA)).isEqualTo(8);
    }

    private int createPayment(long id) {
        try {
            transaction.executeWithoutResult(tx -> paymentService.createPaymentForOrder(id));
            return 200;
        } catch (DuplicateResourceException ex) {
            return 409;
        }
    }

    private List<Integer> concurrent(long id, Supplier<Integer> firstCall, Supplier<Integer> secondCall) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                orders.findByIdForUpdate(id).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock release timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                CountDownLatch started = new CountDownLatch(2);
                Future<Integer> first = pool.submit(() -> {
                    started.countDown();
                    return firstCall.get();
                });
                Future<Integer> second = pool.submit(() -> {
                    started.countDown();
                    return secondCall.get();
                });
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThrows(TimeoutException.class, () -> first.get(300, TimeUnit.MILLISECONDS));
                    assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
                } finally {
                    release.countDown();
                }
                holder.get(10, TimeUnit.SECONDS);
                return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"amount,delivery", "method,delivery", "amount,user-cancel", "method,user-cancel",
            "amount,admin-cancel", "method,admin-cancel"})
    void inconsistentCodPaymentRollsBackOrderAndStockAndCanRetry(String mismatch, String action) throws Exception {
        long id = twoProductOrder();
        boolean delivery = action.equals("delivery");
        if (delivery) {
            data(adminUpdate(id, "CONFIRMED"), 200);
            data(adminUpdate(id, "SHIPPING"), 200);
        }
        PaymentEntity before = payment(id);
        BigDecimal correctAmount = before.getAmount();
        // These snapshots are immutable through JPA; use SQL to simulate inconsistent legacy data.
        BigDecimal storedAmount = mismatch.equals("amount") ? correctAmount.add(BigDecimal.ONE) : correctAmount;
        PaymentMethod storedMethod = mismatch.equals("method") ? PaymentMethod.VNPAY : PaymentMethod.COD;
        jdbc.update("update payments set amount=?, method=? where order_id=?", storedAmount, storedMethod.name(), id);
        assertThat(payment(id).getAmount()).isEqualByComparingTo(storedAmount);
        assertThat(payment(id).getMethod()).isEqualTo(storedMethod);
        LocalDateTime orderUpdatedAt = orders.findById(id).orElseThrow().getUpdatedAt();
        LocalDateTime paymentUpdatedAt = payment(id).getUpdatedAt();

        ResponseEntity<String> response = delivery ? adminUpdate(id, "DELIVERED")
                : action.equals("user-cancel") ? cancel(id) : adminUpdate(id, "CANCELLED");
        error(response, 400);
        assertThat(json.readTree(response.getBody()).path("message").asText()).contains("does not match order");
        assertThat(status(id)).isEqualTo(delivery ? OrderStatus.SHIPPING : OrderStatus.PENDING);
        assertThat(orders.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(orderUpdatedAt);
        assertThat(stock(productA)).isEqualTo(8);
        assertThat(stock(productB)).isEqualTo(7);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment(id).getAmount()).isEqualByComparingTo(storedAmount);
        assertThat(payment(id).getMethod()).isEqualTo(storedMethod);
        assertThat(payment(id).getPaidAt()).isNull();
        assertThat(payment(id).getUpdatedAt()).isEqualTo(paymentUpdatedAt);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(orderItems.count()).isEqualTo(2);
        assertThat(cartItems.count()).isZero();

        jdbc.update("update payments set amount=?, method='COD' where order_id=?", correctAmount, id);
        data(delivery ? adminUpdate(id, "DELIVERED")
                : action.equals("user-cancel") ? cancel(id) : adminUpdate(id, "CANCELLED"), 200);
        assertThat(status(id)).isEqualTo(delivery ? OrderStatus.DELIVERED : OrderStatus.CANCELLED);
        assertThat(payment(id).getStatus()).isEqualTo(delivery ? PaymentStatus.PAID : PaymentStatus.CANCELLED);
        assertThat(stock(productA)).isEqualTo(delivery ? 8 : 10);
        assertThat(stock(productB)).isEqualTo(delivery ? 7 : 10);
    }

    private void assertCheckoutRolledBack(int lines, int quantity) throws Exception {
        assertThat(orders.count()).isZero();
        assertThat(orderItems.count()).isZero();
        assertThat(payments.count()).isZero();
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        JsonNode cart = data(call(HttpMethod.GET, "/api/cart", tokenA, null), 200);
        assertThat(cart.path("items")).hasSize(lines);
        assertThat(cart.path("totalItems").asInt()).isEqualTo(quantity);
    }

    private long createOrder(int quantity) throws Exception {
        add(tokenA, productA, quantity);
        return data(checkout(tokenA), 201).path("id").asLong();
    }

    private long twoProductOrder() throws Exception {
        add(tokenA, productA, 2);
        add(tokenA, productB, 3);
        return data(checkout(tokenA), 201).path("id").asLong();
    }

    private long shippingOrder() throws Exception {
        long id = createOrder(2);
        data(adminUpdate(id, "CONFIRMED"), 200);
        data(adminUpdate(id, "SHIPPING"), 200);
        return id;
    }

    private PaymentEntity payment(long id) {
        return payments.findByOrderId(id).orElseThrow();
    }

    private OrderStatus status(long id) {
        return orders.findById(id).orElseThrow().getStatus();
    }

    private int stock(ProductEntity product) {
        return products.findById(product.getId()).orElseThrow().getQuantity();
    }

    private void add(String token, ProductEntity product, int quantity) throws Exception {
        data(call(HttpMethod.POST, "/api/cart/items", token,
                json.writeValueAsString(Map.of("productId", product.getId(), "quantity", quantity))), 201);
    }

    private String checkoutBody() {
        return """
                {"receiverName":"Customer","phone":"0901234567","shippingAddress":"123 Test Street","paymentMethod":"COD"}
                """;
    }

    private ResponseEntity<String> checkout(String token) {
        return call(HttpMethod.POST, "/api/orders", token, checkoutBody());
    }

    private ResponseEntity<String> cancel(long id) {
        return call(HttpMethod.PUT, "/api/orders/" + id + "/cancel", tokenA, null);
    }

    private ResponseEntity<String> adminUpdate(long id, String status) {
        return call(HttpMethod.PUT, "/api/admin/orders/" + id + "/status", adminToken,
                "{\"status\":\"" + status + "\"}");
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response, int expected) throws Exception {
        assertThat(response.getStatusCode().value()).as("HTTP body: %s", response.getBody()).isEqualTo(expected);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body.path("data");
    }

    private void error(ResponseEntity<String> response, int expected) throws Exception {
        assertThat(response.getStatusCode().value()).as("HTTP body: %s", response.getBody()).isEqualTo(expected);
        assertThat(json.readTree(response.getBody()).path("success").asBoolean()).isFalse();
    }

    private String token(String email, Role role) {
        UserEntity user = users.save(UserEntity.builder().fullName("Customer").email(email)
                .password("unused-test-password").role(role).status(UserStatus.ACTIVE).build());
        return jwtService.generateToken(new CustomUserDetails(user));
    }

    private ProductEntity product(String name, String slug, CategoryEntity category, BrandEntity brand) {
        return ProductEntity.builder().name(name).slug(slug).price(new BigDecimal("100000")).quantity(10)
                .status(ProductStatus.ACTIVE).category(category).brand(brand).build();
    }
}
