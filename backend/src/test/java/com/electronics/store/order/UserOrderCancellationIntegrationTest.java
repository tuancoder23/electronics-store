package com.electronics.store.order;

import com.electronics.store.entity.*;
import com.electronics.store.repository.*;
import com.electronics.store.security.CustomUserDetails;
import com.electronics.store.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Real HTTP/JWT and committed transactions: assertions read the database after each request.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:user_order_cancel_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false"
})
class UserOrderCancellationIntegrationTest {
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
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate jdbc;

    private UserEntity userA;
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
        userA = users.save(user("a@example.com", Role.USER));
        UserEntity userB = users.save(user("b@example.com", Role.USER));
        UserEntity admin = users.save(user("admin@example.com", Role.ADMIN));
        tokenA = jwtService.generateToken(new CustomUserDetails(userA));
        tokenB = jwtService.generateToken(new CustomUserDetails(userB));
        adminToken = jwtService.generateToken(new CustomUserDetails(admin));
        CategoryEntity category = categories.save(CategoryEntity.builder().name("Phones").slug("phones").build());
        BrandEntity brand = brands.save(BrandEntity.builder().name("Acme").slug("acme").build());
        productA = products.save(product("Product A", "product-a", category, brand));
        productB = products.save(product("Product B", "product-b", category, brand));
    }

    @Test
    void pendingCancellationRestoresOrderItemsOnceAndPreservesSnapshotsAndCarts() throws Exception {
        long id = twoProductOrder();
        JsonNode before = data(call(HttpMethod.GET, "/api/orders/" + id, tokenA, null), 200);
        // Current carts and product details must not determine restored quantities or snapshots.
        add(tokenA, productA, 1);
        add(tokenB, productB, 1);
        ProductEntity changed = products.findById(productA.getId()).orElseThrow();
        changed.setName("Renamed product");
        changed.setPrice(new BigDecimal("250000"));
        products.saveAndFlush(changed);

        JsonNode cancelled = data(cancel(id, tokenA), 200);
        assertThat(cancelled.path("id").asLong()).isEqualTo(id);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        for (String field : List.of("items", "subtotal", "shippingFee", "totalAmount", "receiverName",
                "phone", "shippingAddress", "note", "paymentMethod", "createdAt")) {
            assertThat(cancelled.path(field)).as(field).isEqualTo(before.path(field));
        }
        for (String field : List.of("user", "password", "authorities", "accessToken")) {
            assertThat(cancelled.findValues(field)).isEmpty();
        }
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(data(call(HttpMethod.GET, "/api/orders/" + id, tokenA, null), 200)
                .path("status").asText()).isEqualTo("CANCELLED");
        assertThat(data(call(HttpMethod.GET, "/api/orders/my-orders", tokenA, null), 200)
                .path("content").get(0).path("status").asText()).isEqualTo("CANCELLED");
        LocalDateTime cancelledAt = orders.findById(id).orElseThrow().getUpdatedAt();
        error(cancel(id, tokenA), 400);
        error(adminUpdate(id, "CANCELLED"), 400);
        assertThat(orders.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(cancelledAt);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        assertThat(orderItems.count()).isEqualTo(2);
        assertThat(cartItems.count()).isEqualTo(2);
        for (String token : List.of(tokenA, tokenB)) {
            assertThat(data(call(HttpMethod.GET, "/api/cart", token, null), 200)
                    .path("totalItems").asInt()).isEqualTo(1);
        }
    }

    @Test
    void missingOrInvalidJwtCannotCancel() throws Exception {
        long id = createOrder(tokenA, 2);
        error(cancel(id, null), 401);
        error(cancel(id, "invalid"), 401);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "query", "body"})
    void ownershipComesOnlyFromJwtEvenWhenClientSuppliesUserId(String source) throws Exception {
        long id = createOrder(tokenA, 2);
        String path = "/api/orders/" + id + "/cancel";
        String body = null;
        if (source.equals("query")) path += "?userId=" + userA.getId();
        if (source.equals("body")) body = json.writeValueAsString(Map.of("userId", userA.getId()));
        error(call(HttpMethod.PUT, path, tokenB, body), 404);
        // The user endpoint does not grant an admin ownership of someone else's order either.
        error(call(HttpMethod.PUT, path, adminToken, body), 404);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
        data(cancel(id, tokenA), 200);
    }

    @Test
    void unknownOrderAndMalformedIdReturnErrorsWithoutMutations() throws Exception {
        long id = createOrder(tokenA, 2);
        error(cancel(Long.MAX_VALUE, tokenA), 404);
        error(call(HttpMethod.PUT, "/api/orders/invalid/cancel", tokenA, null), 400);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void userCannotCancelAnyNonPendingStatus(OrderStatus from) throws Exception {
        long id = createOrder(tokenA, 2);
        OrderEntity order = orders.findById(id).orElseThrow();
        order.setStatus(from);
        orders.saveAndFlush(order);
        LocalDateTime before = orders.findById(id).orElseThrow().getUpdatedAt();
        ResponseEntity<String> response = cancel(id, tokenA);
        error(response, 400);
        assertThat(json.readTree(response.getBody()).path("message").asText()).contains("Only PENDING");
        assertThat(status(id)).isEqualTo(from);
        assertThat(orders.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(before);
        assertThat(stock(productA)).isEqualTo(8);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"OUT_OF_STOCK", "INACTIVE"})
    void restorationReactivatesOnlyOutOfStockProducts(ProductStatus before) throws Exception {
        long id = createOrder(tokenA, 10);
        assertThat(stock(productA)).isZero();
        ProductEntity product = products.findById(productA.getId()).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        product.setStatus(before);
        products.saveAndFlush(product);
        data(cancel(id, tokenA), 200);
        ProductEntity restored = products.findById(productA.getId()).orElseThrow();
        assertThat(restored.getQuantity()).isEqualTo(10);
        assertThat(restored.getStatus()).isEqualTo(before == ProductStatus.INACTIVE
                ? ProductStatus.INACTIVE : ProductStatus.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "negative", "overflow"})
    void failureOnSecondProductRollsBackEarlierStockAndReactivation(String failure) throws Exception {
        add(tokenA, productA, 10);
        add(tokenA, productB, 3);
        long id = data(checkout(tokenA), 201).path("id").asLong();
        boolean corruptStock = failure.equals("negative");
        List<Map<String, Object>> stockConstraints = List.of();
        if (corruptStock) {
            // Deliberately simulate legacy corrupt data; normal JPA and SQL writes now reject it.
            // Hibernate can emit both the explicit CHECK and one derived from @Min.
            stockConstraints = jdbc.queryForList("""
                    select tc.constraint_name, cc.check_clause
                    from information_schema.table_constraints tc
                    join information_schema.check_constraints cc
                      on tc.constraint_schema = cc.constraint_schema and tc.constraint_name = cc.constraint_name
                    where tc.table_name = 'PRODUCTS' and tc.constraint_type = 'CHECK'
                      and lower(cc.check_clause) like '%quantity%'
                    """);
            assertThat(stockConstraints).isNotEmpty();
            for (Map<String, Object> constraint : stockConstraints) {
                jdbc.execute("alter table products drop constraint \"" + constraint.get("CONSTRAINT_NAME") + "\"");
            }
        }
        try {
            if (failure.equals("missing")) {
                products.deleteById(productB.getId());
            } else if (corruptStock) {
                jdbc.update("update products set quantity=-1 where id=?", productB.getId());
            } else {
                ProductEntity product = products.findById(productB.getId()).orElseThrow();
                product.setQuantity(Integer.MAX_VALUE);
                products.saveAndFlush(product);
            }
            LocalDateTime before = orders.findById(id).orElseThrow().getUpdatedAt();
            error(cancel(id, tokenA), 400);
            assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
            assertThat(orders.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(before);
            ProductEntity first = products.findById(productA.getId()).orElseThrow();
            assertThat(first.getQuantity()).isZero();
            assertThat(first.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
            if (failure.equals("missing")) {
                assertThat(products.findById(productB.getId())).isEmpty();
            } else {
                assertThat(stock(productB)).isEqualTo(corruptStock ? -1 : Integer.MAX_VALUE);
            }
            assertThat(orderItems.count()).isEqualTo(2);
            PaymentEntity payment = payments.findByOrderId(id).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPaidAt()).isNull();
            assertThat(payments.count()).isEqualTo(1);
        } finally {
            if (corruptStock) {
                jdbc.update("update products set quantity=0 where id=?", productB.getId());
                for (Map<String, Object> constraint : stockConstraints) {
                    jdbc.execute("alter table products add constraint \"" + constraint.get("CONSTRAINT_NAME")
                            + "\" check (" + constraint.get("CHECK_CLAUSE") + ")");
                }
            }
        }
    }

    @Test
    void databaseFailureRollsBackAllChangesAndRetryRestoresOnce() throws Exception {
        long id = twoProductOrder();
        LocalDateTime before = orders.findById(id).orElseThrow().getUpdatedAt();
        jdbc.execute("alter table products add constraint test_user_restore_failure check (id <> "
                + productB.getId() + " or quantity <= 7)");
        try {
            error(cancel(id, tokenA), 500);
            assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
            assertThat(orders.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(before);
            assertThat(stock(productA)).isEqualTo(8);
            assertThat(stock(productB)).isEqualTo(7);
        } finally {
            jdbc.execute("alter table products drop constraint test_user_restore_failure");
        }
        data(cancel(id, tokenA), 200);
        error(cancel(id, tokenA), 400);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void simultaneousUserCancellationsRestoreStockOnlyOnce() throws Exception {
        long id = createOrder(tokenA, 2);
        assertThat(concurrent(() -> orders.findByIdForUpdate(id).orElseThrow(),
                () -> cancel(id, tokenA), () -> cancel(id, tokenA)))
                .containsExactlyInAnyOrder(200, 400);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void simultaneousUserAndAdminCancellationsRestoreStockOnlyOnce() throws Exception {
        long id = createOrder(tokenA, 2);
        assertThat(concurrent(() -> orders.findByIdForUpdate(id).orElseThrow(),
                () -> cancel(id, tokenA), () -> adminUpdate(id, "CANCELLED")))
                .containsExactlyInAnyOrder(200, 400);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void simultaneousUserCancellationAndAdminConfirmationRespectWinningTransition() throws Exception {
        long id = createOrder(tokenA, 2);
        List<Integer> results = concurrent(() -> orders.findByIdForUpdate(id).orElseThrow(),
                () -> cancel(id, tokenA), () -> adminUpdate(id, "CONFIRMED"));
        assertThat(results).containsExactlyInAnyOrder(200, 400);
        boolean userWon = results.get(0) == 200;
        assertThat(status(id)).isEqualTo(userWon ? OrderStatus.CANCELLED : OrderStatus.CONFIRMED);
        assertThat(stock(productA)).isEqualTo(userWon ? 10 : 8);
    }

    @Test
    void simultaneousCancellationsOfDifferentOrdersDoNotLoseRestoredStock() throws Exception {
        long first = createOrder(tokenA, 2);
        long second = createOrder(tokenB, 3);
        assertThat(concurrent(() -> products.findByIdForUpdate(productA.getId()).orElseThrow(),
                () -> cancel(first, tokenA), () -> cancel(second, tokenB)))
                .containsExactly(200, 200);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(status(first)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(status(second)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellationAndCheckoutShareProductLockWithoutLosingStock() throws Exception {
        long id = createOrder(tokenA, 2);
        add(tokenB, productA, 3);
        assertThat(concurrent(() -> products.findByIdForUpdate(productA.getId()).orElseThrow(),
                () -> cancel(id, tokenA), () -> checkout(tokenB))).containsExactly(200, 201);
        assertThat(stock(productA)).isEqualTo(7);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orders.count()).isEqualTo(2);
    }

    private List<Integer> concurrent(Runnable lock, Supplier<ResponseEntity<String>> firstCall,
                                     Supplier<ResponseEntity<String>> secondCall) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
                lock.run();
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
                    return firstCall.get().getStatusCode().value();
                });
                Future<Integer> second = pool.submit(() -> {
                    started.countDown();
                    return secondCall.get().getStatusCode().value();
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

    private long twoProductOrder() throws Exception {
        add(tokenA, productA, 2);
        add(tokenA, productB, 3);
        return data(checkout(tokenA), 201).path("id").asLong();
    }

    private long createOrder(String token, int quantity) throws Exception {
        add(token, productA, quantity);
        return data(checkout(token), 201).path("id").asLong();
    }

    private void add(String token, ProductEntity product, int quantity) throws Exception {
        data(call(HttpMethod.POST, "/api/cart/items", token,
                json.writeValueAsString(Map.of("productId", product.getId(), "quantity", quantity))), 201);
    }

    private ResponseEntity<String> checkout(String token) {
        return call(HttpMethod.POST, "/api/orders", token, """
                {"receiverName":"Customer","phone":"0901234567","shippingAddress":"123 Test Street",
                 "note":"Call on arrival","paymentMethod":"COD"}
                """);
    }

    private ResponseEntity<String> cancel(long id, String token) {
        return call(HttpMethod.PUT, "/api/orders/" + id + "/cancel", token, null);
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

    private OrderStatus status(long id) {
        return orders.findById(id).orElseThrow().getStatus();
    }

    private int stock(ProductEntity product) {
        return products.findById(product.getId()).orElseThrow().getQuantity();
    }

    private UserEntity user(String email, Role role) {
        return UserEntity.builder().fullName("Customer").email(email).password("unused-test-password")
                .role(role).status(UserStatus.ACTIVE).build();
    }

    private ProductEntity product(String name, String slug, CategoryEntity category, BrandEntity brand) {
        return ProductEntity.builder().name(name).slug(slug).price(new BigDecimal("100000")).quantity(10)
                .status(ProductStatus.ACTIVE).category(category).brand(brand).build();
    }
}
