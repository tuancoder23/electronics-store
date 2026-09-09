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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Real HTTP/JWT and committed transactions, including rollback and lock contention.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_order_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false"
})
class AdminOrderIntegrationTest {
    private static final String ADMIN = "/api/admin/orders";
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
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
    private String adminToken;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        payments.deleteAll();
        orderItems.deleteAll();
        orders.deleteAll();
        cartItems.deleteAll();
        carts.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        brands.deleteAll();
        users.deleteAll();
        String password = UUID.randomUUID().toString();
        String encoded = passwordEncoder.encode(password);
        users.save(user("admin@example.com", encoded, Role.ADMIN));
        userA = users.save(user("a@example.com", encoded, Role.USER));
        UserEntity userB = users.save(user("b@example.com", encoded, Role.USER));
        adminToken = data(call(HttpMethod.POST, "/api/auth/login", null,
                json.writeValueAsString(Map.of("email", "admin@example.com", "password", password))), 200)
                .path("accessToken").asText();
        assertThat(adminToken).isNotBlank();
        tokenA = jwtService.generateToken(new CustomUserDetails(userA));
        tokenB = jwtService.generateToken(new CustomUserDetails(userB));
        CategoryEntity category = categories.save(CategoryEntity.builder().name("Phones").slug("phones").build());
        BrandEntity brand = brands.save(BrandEntity.builder().name("Acme").slug("acme").build());
        productA = products.save(product("Product A", "product-a", category, brand));
        productB = products.save(product("Product B", "product-b", category, brand));
    }

    @Test
    void adminListIncludesAllUsersWithPaginationAndNewestFirst() throws Exception {
        long first = createOrder(tokenA, "Alice", 2);
        long second = createOrder(tokenB, "Bob", 1);
        // Explicit dates make ordering independent of wall-clock resolution.
        jdbc.update("update orders set created_at=? where id=?", LocalDateTime.of(2026, 1, 1, 12, 0), first);
        jdbc.update("update orders set created_at=? where id=?", LocalDateTime.of(2026, 1, 2, 12, 0), second);
        JsonNode defaults = data(get(ADMIN), 200);
        assertThat(defaults.path("size").asInt()).isEqualTo(20);
        assertThat(defaults.path("totalElements").asInt()).isEqualTo(2);
        JsonNode page = data(get(ADMIN + "?page=0&size=1"), 200);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(second);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(data(get(ADMIN + "?page=1&size=1"), 200).path("content").get(0).path("id").asLong())
                .isEqualTo(first);
        assertThat(data(get(ADMIN + "?page=2&size=1"), 200).path("content")).isEmpty();
        jdbc.update("update orders set created_at=?", LocalDateTime.of(2026, 1, 1, 12, 0));
        assertThat(data(get(ADMIN), 200).path("content").get(0).path("id").asLong()).isEqualTo(second);
    }

    @Test
    void filtersRunTogetherAndMatchReceiverOrPhone() throws Exception {
        long first = createOrder(tokenA, "Alice_100%", 1);
        long second = createOrder(tokenB, "Bob", 1);
        data(update(second, "CONFIRMED"), 200);
        LocalDateTime boundary = LocalDateTime.of(2026, 2, 1, 12, 0);
        jdbc.update("update orders set created_at=? where id=?", boundary, first);
        jdbc.update("update orders set created_at=? where id=?", boundary.plusDays(1), second);
        String query = "?status=PENDING&userId=" + userA.getId()
                + "&keyword=ALICE&fromDate=2026-02-01T12:00:00&toDate=2026-02-01T12:00:00";
        JsonNode filtered = data(get(ADMIN + query), 200);
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);
        assertThat(filtered.path("content").get(0).path("id").asLong()).isEqualTo(first);
        assertThat(data(get(ADMIN + "?status=CONFIRMED"), 200).path("content").get(0).path("id").asLong())
                .isEqualTo(second);
        assertThat(data(get(ADMIN + "?keyword=090123"), 200).path("totalElements").asInt()).isEqualTo(2);
        assertThat(data(get(ADMIN + "?keyword=_"), 200).path("totalElements").asInt()).isEqualTo(1);
        assertThat(data(get(ADMIN + "?keyword=%"), 200).path("totalElements").asInt()).isEqualTo(1);
        assertThat(data(get(ADMIN + "?keyword=missing"), 200).path("content")).isEmpty();
        assertThat(data(get(ADMIN + "?fromDate=2026-02-02T00:00:00"), 200).path("totalElements").asInt()).isEqualTo(1);
        assertThat(data(get(ADMIN + "?toDate=2026-02-01T23:59:59"), 200).path("totalElements").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "status=INVALID", "userId=0",
            "userId=abc", "fromDate=invalid", "fromDate=2026-02-02T00:00:00&toDate=2026-02-01T00:00:00"})
    void invalidListParametersReturn400(String query) throws Exception {
        error(get(ADMIN + "?" + query), 400);
    }

    @Test
    void adminReadsOtherUsersDetailWithoutSecurityData() throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        JsonNode detail = data(get(ADMIN + "/" + id), 200);
        assertThat(detail.path("items")).hasSize(1);
        assertThat(detail.path("totalAmount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(detail.path("paymentMethod").asText()).isEqualTo("COD");
        assertThat(detail.path("createdAt").asText()).isNotBlank();
        assertThat(detail.path("updatedAt").asText()).isNotBlank();
        for (String field : List.of("user", "password", "authorities", "accessToken")) {
            assertThat(detail.findValues(field)).isEmpty();
        }
        error(get(ADMIN + "/999999"), 404);
        error(update(999999, "CONFIRMED"), 404);
    }

    @Test
    void allAdminRoutesEnforceRoleAndJwt() throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        for (String token : new String[]{null, "invalid", tokenA}) {
            int expected = tokenA.equals(token) ? 403 : 401;
            error(call(HttpMethod.GET, ADMIN, token, null), expected);
            error(call(HttpMethod.GET, ADMIN + "/" + id, token, null), expected);
            error(call(HttpMethod.PUT, ADMIN + "/" + id + "/status", token, "{\"status\":\"CANCELLED\"}"), expected);
        }
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
    }

    static Stream<Arguments> transitions() {
        return Arrays.stream(OrderStatus.values()).flatMap(from -> Arrays.stream(OrderStatus.values())
                .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void validatesEveryStatusTransition(OrderStatus from, OrderStatus to) throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        OrderEntity order = orders.findById(id).orElseThrow();
        order.setStatus(from);
        orders.saveAndFlush(order);
        LocalDateTime before = orders.findById(id).orElseThrow().getUpdatedAt();
        boolean allowed = List.of("PENDING:CONFIRMED", "PENDING:CANCELLED", "CONFIRMED:SHIPPING",
                "CONFIRMED:CANCELLED", "SHIPPING:DELIVERED").contains(from + ":" + to);
        ResponseEntity<String> response = update(id, to.name());
        if (allowed) {
            JsonNode result = data(response, 200);
            assertThat(result.path("status").asText()).isEqualTo(to.name());
            assertThat(status(id)).isEqualTo(to);
        } else {
            error(response, 400);
            assertThat(json.readTree(response.getBody()).path("message").asText()).contains(from.name(), to.name());
            assertThat(status(id)).isEqualTo(from);
            assertThat(orders.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(before);
        }
        assertThat(stock(productA)).isEqualTo(allowed && to == OrderStatus.CANCELLED ? 10 : 8);
    }

    @Test
    void completeDeliveryFlowKeepsStockAndUserCanReadNewStatus() throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        for (String status : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            data(update(id, status), 200);
        }
        error(update(id, "CANCELLED"), 400);
        assertThat(stock(productA)).isEqualTo(8);
        assertThat(data(call(HttpMethod.GET, "/api/orders/" + id, tokenA, null), 200)
                .path("status").asText()).isEqualTo("DELIVERED");
        error(call(HttpMethod.GET, "/api/orders/" + id, tokenB, null), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"INVALID\"}", "{\"status\":0}",
            "{\"status\":\"1\"}", "{\"status\":true}", "{\"status\":\"confirmed\"}", "{", "[]"})
    void malformedStatusRequestsAreRejected(String body) throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        error(call(HttpMethod.PUT, ADMIN + "/" + id + "/status", adminToken, body), 400);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"totalAmount", "userId", "stock", "items", "OrderItems"})
    void clientCannotUpdateBackendControlledFields(String field) throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        error(call(HttpMethod.PUT, ADMIN + "/" + id + "/status", adminToken,
                json.writeValueAsString(Map.of("status", "CANCELLED", field, 123))), 400);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
    }

    @Test
    void cancelRestoresMultipleProductsExactlyOnceWithoutChangingSnapshots() throws Exception {
        add(tokenA, productA, 2);
        add(tokenA, productB, 3);
        long id = data(checkout(tokenA, "Alice"), 201).path("id").asLong();
        JsonNode before = data(get(ADMIN + "/" + id), 200);
        assertThat(stock(productA)).isEqualTo(8);
        assertThat(stock(productB)).isEqualTo(7);
        JsonNode cancelled = data(update(id, "CANCELLED"), 200);
        assertThat(cancelled.path("items")).isEqualTo(before.path("items"));
        assertThat(cancelled.path("totalAmount")).isEqualTo(before.path("totalAmount"));
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        error(update(id, "CANCELLED"), 400);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
        assertThat(cartItems.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OUT_OF_STOCK", "INACTIVE"})
    void restorationReactivatesOnlyOutOfStockProducts(String productStatus) throws Exception {
        long id = createOrder(tokenA, "Alice", 10);
        ProductEntity product = products.findById(productA.getId()).orElseThrow();
        product.setStatus(ProductStatus.valueOf(productStatus));
        products.saveAndFlush(product);
        data(update(id, "CANCELLED"), 200);
        ProductEntity restored = products.findById(productA.getId()).orElseThrow();
        assertThat(restored.getQuantity()).isEqualTo(10);
        assertThat(restored.getStatus()).isEqualTo(productStatus.equals("INACTIVE") ? ProductStatus.INACTIVE : ProductStatus.ACTIVE);
    }

    @Test
    void missingSecondProductRollsBackEarlierRestoration() throws Exception {
        long id = twoProductOrder();
        products.deleteById(productB.getId());
        error(update(id, "CANCELLED"), 400);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
        assertThat(orderItems.count()).isEqualTo(2);
    }

    @Test
    void stockOverflowRollsBackEarlierRestoration() throws Exception {
        long id = twoProductOrder();
        ProductEntity product = products.findById(productB.getId()).orElseThrow();
        product.setQuantity(Integer.MAX_VALUE);
        products.saveAndFlush(product);
        error(update(id, "CANCELLED"), 400);
        assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(productA)).isEqualTo(8);
        assertThat(stock(productB)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void databaseFailureRollsBackStatusAndAllStock() throws Exception {
        long id = twoProductOrder();
        jdbc.execute("alter table products add constraint test_restore_failure check (id <> "
                + productB.getId() + " or quantity <= 7)");
        try {
            error(update(id, "CANCELLED"), 500);
            assertThat(status(id)).isEqualTo(OrderStatus.PENDING);
            assertThat(stock(productA)).isEqualTo(8);
            assertThat(stock(productB)).isEqualTo(7);
        } finally {
            jdbc.execute("alter table products drop constraint test_restore_failure");
        }
        data(update(id, "CANCELLED"), 200);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(10);
    }

    @Test
    void simultaneousCancelRestoresStockOnlyOnce() throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        assertThat(concurrent(() -> orders.findByIdForUpdate(id).orElseThrow(),
                () -> update(id, "CANCELLED"), () -> update(id, "CANCELLED")))
                .containsExactlyInAnyOrder(200, 400);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(status(id)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void simultaneousCancellationOfDifferentOrdersDoesNotLoseRestoredStock() throws Exception {
        long first = createOrder(tokenA, "Alice", 2);
        long second = createOrder(tokenB, "Bob", 3);
        assertThat(concurrent(() -> products.findByIdForUpdate(productA.getId()).orElseThrow(),
                () -> update(first, "CANCELLED"), () -> update(second, "CANCELLED")))
                .containsExactly(200, 200);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(status(first)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(status(second)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelAndCheckoutShareProductLockWithoutLosingStock() throws Exception {
        long id = createOrder(tokenA, "Alice", 2);
        add(tokenB, productA, 3);
        assertThat(concurrent(() -> products.findByIdForUpdate(productA.getId()).orElseThrow(),
                () -> update(id, "CANCELLED"), () -> checkout(tokenB, "Bob")))
                .containsExactly(200, 201);
        assertThat(stock(productA)).isEqualTo(7);
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
                assertThat(second.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    private long twoProductOrder() throws Exception {
        add(tokenA, productA, 2);
        add(tokenA, productB, 3);
        return data(checkout(tokenA, "Alice"), 201).path("id").asLong();
    }

    private long createOrder(String token, String receiver, int quantity) throws Exception {
        add(token, productA, quantity);
        return data(checkout(token, receiver), 201).path("id").asLong();
    }

    private void add(String token, ProductEntity product, int quantity) throws Exception {
        data(call(HttpMethod.POST, "/api/cart/items", token,
                json.writeValueAsString(Map.of("productId", product.getId(), "quantity", quantity))), 201);
    }

    private ResponseEntity<String> checkout(String token, String receiver) {
        return call(HttpMethod.POST, "/api/orders", token, "{\"receiverName\":\"" + receiver
                + "\",\"phone\":\"0901234567\",\"shippingAddress\":\"123 Test Street\",\"paymentMethod\":\"COD\"}");
    }

    private ResponseEntity<String> update(long id, String status) {
        return call(HttpMethod.PUT, ADMIN + "/" + id + "/status", adminToken, "{\"status\":\"" + status + "\"}");
    }

    private ResponseEntity<String> get(String path) {
        return call(HttpMethod.GET, path, adminToken, null);
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

    private UserEntity user(String email, String password, Role role) {
        return UserEntity.builder().fullName("Customer").email(email).password(password)
                .role(role).status(UserStatus.ACTIVE).build();
    }

    private ProductEntity product(String name, String slug, CategoryEntity category, BrandEntity brand) {
        return ProductEntity.builder().name(name).slug(slug).price(new BigDecimal("100000")).quantity(10)
                .status(ProductStatus.ACTIVE).category(category).brand(brand).build();
    }
}
