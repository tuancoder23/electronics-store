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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

// Real HTTP requests, real JWT filter and committed transactions (no test-level rollback).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:checkout_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false"
})
class OrderIntegrationTest {
    private static final String CHECKOUT = """
            {"receiverName":"Customer A","phone":"0901234567","shippingAddress":"123 Test Street",
             "note":"Call on arrival","paymentMethod":"COD"}
            """;

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
    @Autowired TransactionTemplate transaction;

    private UserEntity userA;
    private ProductEntity productA;
    private ProductEntity productB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        orderItems.deleteAll();
        orders.deleteAll();
        cartItems.deleteAll();
        carts.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        brands.deleteAll();
        users.deleteAll();

        userA = users.save(user("a@example.com"));
        UserEntity userB = users.save(user("b@example.com"));
        CategoryEntity category = categories.save(CategoryEntity.builder().name("Phones").slug("phones").build());
        BrandEntity brand = brands.save(BrandEntity.builder().name("Acme").slug("acme").build());
        productA = products.save(product("Product A", "product-a", "100000", 10, category, brand));
        productB = products.save(product("Product B", "product-b", "50000", 10, category, brand));
        tokenA = jwtService.generateToken(new CustomUserDetails(userA));
        tokenB = jwtService.generateToken(new CustomUserDetails(userB));
    }

    @Test
    void checkoutDecrementsStockAndClearsOnlyCurrentCart() throws Exception {
        add(tokenA, productA, 3);
        add(tokenB, productB, 1);
        JsonNode order = data(checkout(tokenA), 201);
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("paymentMethod").asText()).isEqualTo("COD");
        assertThat(order.path("subtotal").decimalValue()).isEqualByComparingTo("300000");
        assertThat(order.path("shippingFee").decimalValue()).isEqualByComparingTo("0");
        assertThat(order.path("items").get(0).path("id").asLong()).isPositive();
        assertThat(order.path("createdAt").asText()).isNotBlank();
        assertThat(order.path("updatedAt").asText()).isNotBlank();
        assertThat(order.has("user")).isFalse();
        assertThat(order.has("password")).isFalse();
        assertThat(stock(productA)).isEqualTo(7);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(orderItems.count()).isEqualTo(1);
        JsonNode cart = data(call(HttpMethod.GET, "/api/cart", tokenA, null), 200);
        assertThat(cart.path("items")).isEmpty();
        assertThat(cart.path("totalItems").asInt()).isZero();
        assertThat(cart.path("subtotal").decimalValue()).isEqualByComparingTo("0");
        assertThat(data(call(HttpMethod.GET, "/api/cart", tokenB, null), 200).path("totalItems").asInt()).isEqualTo(1);
        assertThat(carts.findByUserId(userA.getId())).isPresent();
        assertThat(users.count()).isEqualTo(2);
        assertThat(products.count()).isEqualTo(2);
    }

    @Test
    void multipleProductsHaveCorrectTotals() throws Exception {
        add(tokenA, productA, 2);
        add(tokenA, productB, 3);
        JsonNode order = data(checkout(tokenA), 201);
        assertThat(order.path("items")).hasSize(2);
        assertThat(order.path("items").get(0).path("lineTotal").decimalValue()).isEqualByComparingTo("200000");
        assertThat(order.path("items").get(1).path("lineTotal").decimalValue()).isEqualByComparingTo("150000");
        assertThat(order.path("subtotal").decimalValue()).isEqualByComparingTo("350000");
        assertThat(order.path("totalAmount").decimalValue()).isEqualByComparingTo("350000");
        assertThat(stock(productA)).isEqualTo(8);
        assertThat(stock(productB)).isEqualTo(7);
    }

    @Test
    void insufficientStockPreservesCartAndCreatesNoOrder() throws Exception {
        add(tokenA, productA, 3);
        productA.setQuantity(2);
        products.save(productA);
        assertThat(checkout(tokenA).getStatusCode().value()).isEqualTo(400);
        assertThat(stock(productA)).isEqualTo(2);
        assertUnchangedCart(1, 3);
    }

    @Test
    void failureOnSecondProductRollsBackFirstProductUpdate() throws Exception {
        add(tokenA, productA, 2);
        add(tokenA, productB, 3);
        productB.setQuantity(2);
        products.save(productB);
        assertThat(checkout(tokenA).getStatusCode().value()).isEqualTo(400);
        assertThat(stock(productA)).isEqualTo(10);
        assertThat(stock(productB)).isEqualTo(2);
        assertUnchangedCart(2, 5);
    }

    @Test
    void databaseFailureAfterOrderInsertRollsBackStockAndOrder() throws Exception {
        add(tokenA, productA, 1);
        // Existing ProductEntity permits blank names. OrderItem snapshot validation fails on insert.
        productA.setName("");
        products.save(productA);
        assertThat(checkout(tokenA).getStatusCode().value()).isEqualTo(500);
        assertThat(stock(productA)).isEqualTo(10);
        assertUnchangedCart(1, 1);
    }

    @Test
    void priceAndNameSnapshotsSurviveProductUpdatesAndDeletion() throws Exception {
        productA.setPrice(new BigDecimal("25990000"));
        productA.setDiscountPrice(new BigDecimal("23990000"));
        products.save(productA);
        add(tokenA, productA, 1);
        long id = data(checkout(tokenA), 201).path("id").asLong();
        productA = products.findById(productA.getId()).orElseThrow();
        productA.setPrice(new BigDecimal("30000000"));
        productA.setDiscountPrice(null);
        productA.setName("New name");
        products.save(productA);
        assertSnapshot(id);
        products.deleteById(productA.getId());
        assertSnapshot(id);
    }

    @Test
    void checkoutUsesPriceChangedAfterAddingToCart() throws Exception {
        add(tokenA, productA, 1);
        productA.setPrice(new BigDecimal("200000"));
        productA.setDiscountPrice(new BigDecimal("150000"));
        products.save(productA);
        assertThat(data(checkout(tokenA), 201).path("items").get(0).path("unitPrice").decimalValue())
                .isEqualByComparingTo("150000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100000", "120000"})
    void invalidDiscountFallsBackToBasePrice(String discount) throws Exception {
        productA.setDiscountPrice(new BigDecimal(discount));
        products.save(productA);
        add(tokenA, productA, 1);
        assertThat(data(checkout(tokenA), 201).path("items").get(0).path("unitPrice").decimalValue())
                .isEqualByComparingTo("100000");
    }

    @Test
    void zeroDiscountPriceIsValidAndZeroStockChangesStatus() throws Exception {
        productA.setDiscountPrice(BigDecimal.ZERO);
        products.save(productA);
        add(tokenA, productA, 10);
        assertThat(data(checkout(tokenA), 201).path("totalAmount").decimalValue()).isEqualByComparingTo("0");
        ProductEntity updated = products.findById(productA.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isZero();
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "OUT_OF_STOCK"})
    void unsellableProductsCannotBeCheckedOut(String status) throws Exception {
        add(tokenA, productA, 1);
        productA.setStatus(ProductStatus.valueOf(status));
        products.save(productA);
        assertThat(checkout(tokenA).getStatusCode().value()).isEqualTo(400);
        assertThat(stock(productA)).isEqualTo(10);
        assertUnchangedCart(1, 1);
    }

    @Test
    void missingAndEmptyCartReturnBusinessError() throws Exception {
        ResponseEntity<String> missing = checkout(tokenA);
        assertThat(missing.getStatusCode().value()).isEqualTo(400);
        assertThat(json.readTree(missing.getBody()).path("message").asText()).isEqualTo("Cart is empty.");
        data(call(HttpMethod.GET, "/api/cart", tokenA, null), 200);
        assertThat(checkout(tokenA).getStatusCode().value()).isEqualTo(400);
        assertThat(orders.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"VNPay\"", "\"MoMo\"", "\"Stripe\"", "0", "null", "\"\""})
    void unsupportedOrMissingPaymentIsRejected(String method) {
        String body = CHECKOUT.replace("\"COD\"", method);
        assertThat(call(HttpMethod.POST, "/api/orders", tokenA, body).getStatusCode().value()).isEqualTo(400);
        assertThat(orders.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"receiverName", "phone", "shippingAddress"})
    void blankRequiredFieldsAreRejected(String field) throws Exception {
        var request = json.readTree(CHECKOUT);
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).put(field, " ");
        assertThat(call(HttpMethod.POST, "/api/orders", tokenA, request.toString()).getStatusCode().value()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "cartId", "subtotal", "totalAmount", "orderStatus", "price"})
    void clientCannotSupplyBackendControlledFields(String field) throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(CHECKOUT);
        request.put(field, 1);
        assertThat(call(HttpMethod.POST, "/api/orders", tokenA, request.toString()).getStatusCode().value()).isEqualTo(400);
        assertThat(orders.count()).isZero();
    }

    @Test
    void orderRoutesRequireJwtAndEnforceOwnership() throws Exception {
        assertThat(checkout(null).getStatusCode().value()).isEqualTo(401);
        assertThat(checkout("invalid").getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.GET, "/api/orders/my-orders", null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.GET, "/api/orders/1", null, null).getStatusCode().value()).isEqualTo(401);
        add(tokenA, productA, 1);
        long id = data(checkout(tokenA), 201).path("id").asLong();
        data(call(HttpMethod.GET, "/api/orders/" + id, tokenA, null), 200);
        assertThat(call(HttpMethod.GET, "/api/orders/" + id, tokenB, null).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.GET, "/api/orders/999999", tokenA, null).getStatusCode().value()).isEqualTo(404);
        assertThat(data(call(HttpMethod.GET, "/api/orders/my-orders?userId=" + userA.getId(), tokenB, null), 200)
                .path("content")).isEmpty();
    }

    @Test
    void orderListIsPaginatedAndNewestFirst() throws Exception {
        add(tokenA, productA, 1);
        long first = data(checkout(tokenA), 201).path("id").asLong();
        add(tokenA, productA, 1);
        long second = data(checkout(tokenA), 201).path("id").asLong();
        JsonNode page = data(call(HttpMethod.GET, "/api/orders/my-orders?page=0&size=1", tokenA, null), 200);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(second);
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(data(call(HttpMethod.GET, "/api/orders/my-orders?page=1&size=1", tokenA, null), 200)
                .path("content").get(0).path("id").asLong()).isEqualTo(first);
        for (String query : List.of("page=-1", "size=0", "size=101", "page=abc")) {
            assertThat(call(HttpMethod.GET, "/api/orders/my-orders?" + query, tokenA, null)
                    .getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void concurrentCheckoutsCannotOversellLastUnit() throws Exception {
        productA.setQuantity(1);
        products.save(productA);
        add(tokenA, productA, 1);
        add(tokenB, productA, 1);
        assertThat(concurrentCheckout(tokenA, tokenB)).containsExactlyInAnyOrder(201, 400);
        assertThat(stock(productA)).isZero();
        assertThat(orders.count()).isEqualTo(1);
        assertThat(orderItems.count()).isEqualTo(1);
        assertThat(cartItems.count()).isEqualTo(1);
    }

    @Test
    void concurrentCheckoutOfSameCartCreatesOnlyOneOrder() throws Exception {
        add(tokenA, productA, 3);
        assertThat(concurrentCheckout(tokenA, tokenA)).containsExactlyInAnyOrder(201, 400);
        assertThat(stock(productA)).isEqualTo(7);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(cartItems.count()).isZero();
    }

    @Test
    void publicCatalogSearchAuthenticationAndCartStillWork() throws Exception {
        for (String path : List.of("/api/health", "/api/categories", "/api/brands", "/api/products",
                "/api/products?keyword=Product&minPrice=60000&sort=price,asc&page=0&size=1")) {
            data(call(HttpMethod.GET, path, null, null), 200);
        }
        JsonNode search = data(call(HttpMethod.GET,
                "/api/products?keyword=Product&minPrice=60000&sort=price,asc&page=0&size=1", null, null), 200);
        assertThat(search.path("content")).hasSize(1);
        assertThat(search.path("content").get(0).path("id").asLong()).isEqualTo(productA.getId());
        String password = UUID.randomUUID().toString();
        String registration = json.writeValueAsString(java.util.Map.of("fullName", "New Customer",
                "email", "new@example.com", "password", password));
        data(call(HttpMethod.POST, "/api/auth/register", null, registration), 201);
        JsonNode login = data(call(HttpMethod.POST, "/api/auth/login", null,
                json.writeValueAsString(java.util.Map.of("email", "new@example.com", "password", password))), 200);
        String token = login.path("accessToken").asText();
        assertThat(token).isNotBlank();
        data(call(HttpMethod.GET, "/api/users/me", token, null), 200);
        data(call(HttpMethod.GET, "/api/cart", token, null), 200);
        add(token, productA, 1);
    }

    private List<Integer> concurrentCheckout(String firstToken, String secondToken) throws Exception {
        // Hold the product lock while both real HTTP requests start, then release it.
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
                products.findByIdForUpdate(productA.getId()).orElseThrow();
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch started = new CountDownLatch(2);
            Future<Integer> first = pool.submit(() -> {
                started.countDown();
                return checkout(firstToken).getStatusCode().value();
            });
            Future<Integer> second = pool.submit(() -> {
                started.countDown();
                return checkout(secondToken).getStatusCode().value();
            });
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                // Neither checkout may finish while the database row is locked.
                org.junit.jupiter.api.Assertions.assertThrows(TimeoutException.class,
                        () -> first.get(300, TimeUnit.MILLISECONDS));
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

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test latch");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void assertSnapshot(long id) throws Exception {
        JsonNode item = data(call(HttpMethod.GET, "/api/orders/" + id, tokenA, null), 200).path("items").get(0);
        assertThat(item.path("unitPrice").decimalValue()).isEqualByComparingTo("23990000");
        assertThat(item.path("productName").asText()).isEqualTo("Product A");
    }

    private void assertUnchangedCart(int lines, int quantity) throws Exception {
        assertThat(orders.count()).isZero();
        assertThat(orderItems.count()).isZero();
        JsonNode cart = data(call(HttpMethod.GET, "/api/cart", tokenA, null), 200);
        assertThat(cart.path("items")).hasSize(lines);
        assertThat(cart.path("totalItems").asInt()).isEqualTo(quantity);
    }

    private int stock(ProductEntity product) {
        return products.findById(product.getId()).orElseThrow().getQuantity();
    }

    private void add(String token, ProductEntity product, int quantity) throws Exception {
        data(call(HttpMethod.POST, "/api/cart/items", token,
                "{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}"), 201);
    }

    private ResponseEntity<String> checkout(String token) {
        return call(HttpMethod.POST, "/api/orders", token, CHECKOUT);
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response, int expectedStatus) throws Exception {
        assertThat(response.getStatusCode().value()).as("HTTP response: %s", response.getBody()).isEqualTo(expectedStatus);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body.path("data");
    }

    private UserEntity user(String email) {
        return UserEntity.builder().fullName("Customer").email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.USER).status(UserStatus.ACTIVE).build();
    }

    private ProductEntity product(String name, String slug, String price, int quantity,
                                  CategoryEntity category, BrandEntity brand) {
        return ProductEntity.builder().name(name).slug(slug).price(new BigDecimal(price)).quantity(quantity)
                .status(ProductStatus.ACTIVE).category(category).brand(brand).build();
    }
}
