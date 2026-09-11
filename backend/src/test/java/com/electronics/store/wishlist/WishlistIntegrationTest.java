package com.electronics.store.wishlist;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Real HTTP, JWT filter and committed H2 transactions; no test-level rollback masks failures.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:wishlist_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false"
})
class WishlistIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired WishlistItemRepository wishlist;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Value("${app.jwt.secret}") String testJwtSecret;

    private UserEntity userA;
    private String tokenA;
    private String tokenB;
    private String adminToken;
    private ProductEntity product;
    private BrandEntity brand;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        wishlist.deleteAll();
        payments.deleteAll();
        orderItems.deleteAll();
        orders.deleteAll();
        cartItems.deleteAll();
        carts.deleteAll();
        products.deleteAll();
        brands.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        userA = user("a@example.com", Role.USER);
        tokenA = token(userA);
        tokenB = token(user("b@example.com", Role.USER));
        adminToken = token(user("admin@example.com", Role.ADMIN));
        brand = brands.save(BrandEntity.builder().name("Acme").slug("acme").build());
        category = categories.save(CategoryEntity.builder().name("Phones").slug("phones").build());
        product = product("phone");
    }

    @Test
    void emptyWishlistReturnsEmptyPageWithoutCreatingData() throws Exception {
        JsonNode page = data(get(tokenA, ""));
        assertThat(page.path("content")).isEmpty();
        assertThat(page.path("totalElements").asLong()).isZero();
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(12);
        assertThat(wishlist.count()).isZero();
        assertNoCommerceData();
    }

    @Test
    void addReturnsSafeProductDetailsAndDuplicatePreservesOriginalItem() throws Exception {
        JsonNode added = data(add(tokenA, product.getId()));
        assertThat(added.path("id").asLong()).isPositive();
        assertThat(added.path("productId").asLong()).isEqualTo(product.getId());
        assertThat(added.path("productName").asText()).isEqualTo(product.getName());
        assertThat(added.path("slug").asText()).isEqualTo("phone");
        assertThat(added.path("price").decimalValue()).isEqualByComparingTo("100000");
        assertThat(added.path("discountPrice").decimalValue()).isEqualByComparingTo("90000");
        assertThat(added.path("thumbnailUrl").asText()).isEqualTo("https://example.test/phone.png");
        assertThat(added.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(added.path("brand").path("id").asLong()).isEqualTo(brand.getId());
        assertThat(added.path("category").path("id").asLong()).isEqualTo(category.getId());
        assertThat(added.path("createdAt").asText()).isNotBlank();
        assertThat(data(add(tokenA, product.getId()))).isEqualTo(added);
        JsonNode listed = data(get(tokenA, "")).path("content").get(0);
        assertThat(listed).isEqualTo(added);
        for (String field : List.of("user", "userId", "password", "authorities", "accessToken", "description", "quantity")) {
            assertThat(listed.findValues(field)).isEmpty();
        }
        assertThat(wishlist.count()).isEqualTo(1);
        assertThat(stock()).isEqualTo(10);
        assertNoCommerceData();
    }

    @Test
    void addMissingProductReturns404WithoutCreatingItem() throws Exception {
        error(add(tokenA, Long.MAX_VALUE), 404);
        assertThat(wishlist.count()).isZero();
        assertNoCommerceData();
    }

    @Test
    void removeAndRepeatedRemoveAreIdempotentAndKeepProductStock() throws Exception {
        data(add(tokenA, product.getId()));
        data(remove(tokenA, product.getId()));
        data(remove(tokenA, product.getId()));
        data(remove(tokenA, Long.MAX_VALUE));
        assertThat(data(get(tokenA, "")).path("content")).isEmpty();
        assertThat(wishlist.count()).isZero();
        assertThat(products.existsById(product.getId())).isTrue();
        assertThat(stock()).isEqualTo(10);
        assertNoCommerceData();
    }

    @Test
    void ownershipUsesAuthenticatedUserAndIgnoresSpoofedUserId() throws Exception {
        data(add(tokenA, product.getId()));
        assertThat(data(get(tokenB, "?userId=" + userA.getId())).path("content")).isEmpty();
        data(call(HttpMethod.DELETE, "/api/wishlist/" + product.getId() + "?userId=" + userA.getId(), tokenB,
                "{\"userId\":" + userA.getId() + "}"));
        assertThat(wishlist.count()).isEqualTo(1);
        data(call(HttpMethod.POST, "/api/wishlist/" + product.getId() + "?userId=" + userA.getId(), tokenB,
                "{\"userId\":" + userA.getId() + "}"));
        assertThat(wishlist.count()).isEqualTo(2);
        assertThat(data(get(tokenA, "")).path("content")).hasSize(1);
        assertThat(data(get(tokenB, "")).path("content")).hasSize(1);
        data(remove(tokenB, product.getId()));
        assertThat(wishlist.findByUserIdAndProductId(userA.getId(), product.getId())).isPresent();
    }

    @Test
    void adminHasAnIndependentPersonalWishlist() throws Exception {
        data(add(tokenA, product.getId()));
        assertThat(data(get(adminToken, "")).path("content")).isEmpty();
        data(add(adminToken, product.getId()));
        assertThat(wishlist.count()).isEqualTo(2);
        data(remove(adminToken, product.getId()));
        assertThat(wishlist.findByUserIdAndProductId(userA.getId(), product.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid", "expired", "inactive"})
    void allEndpointsRequireValidActiveJwt(String mode) throws Exception {
        String auth = switch (mode) {
            case "missing" -> null;
            case "invalid" -> "invalid-token";
            case "expired" -> expiredToken();
            case "inactive" -> {
                userA.setStatus(UserStatus.INACTIVE);
                users.saveAndFlush(userA);
                yield tokenA;
            }
            default -> throw new AssertionError(mode);
        };
        error(get(auth, ""), 401);
        error(add(auth, product.getId()), 401);
        error(remove(auth, product.getId()), 401);
        assertThat(wishlist.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ProductStatus.class)
    void everyProductStatusCanBeAddedAndCurrentDetailsAreReturned(ProductStatus status) throws Exception {
        product.setStatus(status);
        product.setQuantity(status == ProductStatus.OUT_OF_STOCK ? 0 : 10);
        products.saveAndFlush(product);
        data(add(tokenA, product.getId()));
        JsonNode original = data(get(tokenA, "")).path("content").get(0);
        assertThat(original.path("status").asText()).isEqualTo(status.name());
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setQuantity(0);
        product.setPrice(new BigDecimal("120000"));
        product.setDiscountPrice(null);
        products.saveAndFlush(product);
        JsonNode current = data(get(tokenA, "")).path("content").get(0);
        assertThat(current.path("status").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(current.path("price").decimalValue()).isEqualByComparingTo("120000");
        assertThat(current.path("discountPrice").isNull()).isTrue();
        assertThat(current.path("createdAt")).isEqualTo(original.path("createdAt"));
        assertThat(wishlist.count()).isEqualTo(1);
        assertThat(stock()).isZero();
    }

    @Test
    void paginationUsesNewestFirstAndIdBreaksTimestampTies() throws Exception {
        ProductEntity second = product("second");
        ProductEntity third = product("third");
        data(add(tokenA, product.getId()));
        data(add(tokenA, second.getId()));
        data(add(tokenA, third.getId()));
        jdbc.update("update wishlist_items set created_at=?", LocalDateTime.of(2026, 1, 2, 0, 0));
        // The smallest ID is newest: tests date ordering separately from the ID tie-breaker.
        jdbc.update("update wishlist_items set created_at=? where product_id=?",
                LocalDateTime.of(2026, 1, 3, 0, 0), product.getId());
        data(add(tokenB, second.getId()));
        JsonNode firstPage = data(get(tokenA, "?page=0&size=2"));
        assertThat(firstPage.path("content")).hasSize(2);
        assertThat(firstPage.path("content").get(0).path("productId").asLong()).isEqualTo(product.getId());
        assertThat(firstPage.path("content").get(1).path("productId").asLong()).isEqualTo(third.getId());
        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        JsonNode lastPage = data(get(tokenA, "?page=1&size=2"));
        assertThat(lastPage.path("content")).hasSize(1);
        assertThat(lastPage.path("content").get(0).path("productId").asLong()).isEqualTo(second.getId());
        assertThat(lastPage.path("last").asBoolean()).isTrue();
        assertThat(data(get(tokenA, "?page=2&size=2")).path("content")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=-1", "?size=101", "?page=abc", "?size=2147483648"})
    void invalidPaginationReturns400(String query) throws Exception {
        error(get(tokenA, query), 400);
        assertThat(wishlist.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidProductIdReturns400ForBothMutations(String id) throws Exception {
        error(call(HttpMethod.POST, "/api/wishlist/" + id, tokenA, null), 400);
        error(call(HttpMethod.DELETE, "/api/wishlist/" + id, tokenA, null), 400);
        assertThat(wishlist.count()).isZero();
    }

    @Test
    void wishlistDoesNotCreateCartOrModifyExistingCart() throws Exception {
        data(add(tokenA, product.getId()));
        assertThat(carts.count()).isZero();
        assertThat(cartItems.count()).isZero();
        assertThat(call(HttpMethod.POST, "/api/cart/items", tokenA,
                "{\"productId\":" + product.getId() + ",\"quantity\":2}").getStatusCode().value()).isEqualTo(201);
        JsonNode before = data(call(HttpMethod.GET, "/api/cart", tokenA, null));
        data(remove(tokenA, product.getId()));
        data(add(tokenA, product.getId()));
        assertThat(data(call(HttpMethod.GET, "/api/cart", tokenA, null))).isEqualTo(before);
        assertThat(stock()).isEqualTo(10);
        assertThat(orders.count()).isZero();
        assertThat(payments.count()).isZero();
    }

    @Test
    void wishlistDoesNotModifyExistingOrderOrPayment() throws Exception {
        call(HttpMethod.POST, "/api/cart/items", tokenA,
                "{\"productId\":" + product.getId() + ",\"quantity\":2}");
        ResponseEntity<String> checkout = call(HttpMethod.POST, "/api/orders", tokenA,
                "{\"receiverName\":\"Customer\",\"phone\":\"0901234567\",\"shippingAddress\":\"Test street\",\"paymentMethod\":\"COD\"}");
        assertThat(checkout.getStatusCode().value()).isEqualTo(201);
        long orderId = json.readTree(checkout.getBody()).path("data").path("id").asLong();
        JsonNode before = data(call(HttpMethod.GET, "/api/orders/" + orderId, tokenA, null));
        data(add(tokenA, product.getId()));
        data(remove(tokenA, product.getId()));
        assertThat(data(call(HttpMethod.GET, "/api/orders/" + orderId, tokenA, null))).isEqualTo(before);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(stock()).isEqualTo(8);
        assertThat(cartItems.count()).isZero();
    }

    @Test
    void databaseUniqueConstraintRejectsBypassingService() throws Exception {
        data(add(tokenA, product.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> wishlist.saveAndFlush(
                WishlistItemEntity.builder().user(userA).product(product).build()));
        assertThat(wishlist.count()).isEqualTo(1);
    }

    @Test
    void persistenceFailureRollsBackAndFollowingRequestCanRetry() throws Exception {
        jdbc.execute("alter table wishlist_items add constraint test_wishlist_failure check (product_id < 0)");
        try {
            error(add(tokenA, product.getId()), 500);
            assertThat(wishlist.count()).isZero();
            assertThat(stock()).isEqualTo(10);
            assertNoCommerceData();
        } finally {
            jdbc.execute("alter table wishlist_items drop constraint test_wishlist_failure");
        }
        data(add(tokenA, product.getId()));
        assertThat(wishlist.count()).isEqualTo(1);
    }

    @Test
    void productHardDeletionCleansWishlistWithoutBreakingExistingAdminApi() throws Exception {
        data(add(tokenA, product.getId()));
        data(add(tokenB, product.getId()));
        data(call(HttpMethod.DELETE, "/api/admin/products/" + product.getId(), adminToken, null));
        assertThat(wishlist.count()).isZero();
        assertThat(data(get(tokenA, "")).path("content")).isEmpty();
        assertThat(data(get(tokenB, "")).path("content")).isEmpty();
        assertThat(users.existsById(userA.getId())).isTrue();
    }

    @Test
    void deletingUserCleansOnlyTheirWishlistAndKeepsProduct() throws Exception {
        data(add(tokenA, product.getId()));
        data(add(tokenB, product.getId()));
        users.deleteById(userA.getId());
        assertThat(wishlist.count()).isEqualTo(1);
        assertThat(data(get(tokenB, "")).path("content")).hasSize(1);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void concurrentAddsReturnSameItemAndConcurrentRemovesSucceed() throws Exception {
        List<ResponseEntity<String>> added = concurrent(() -> add(tokenA, product.getId()),
                () -> add(tokenA, product.getId()));
        assertThat(data(added.get(0))).isEqualTo(data(added.get(1)));
        assertThat(wishlist.count()).isEqualTo(1);
        List<ResponseEntity<String>> removed = concurrent(() -> remove(tokenA, product.getId()),
                () -> remove(tokenA, product.getId()));
        data(removed.get(0));
        data(removed.get(1));
        assertThat(wishlist.count()).isZero();
        assertThat(stock()).isEqualTo(10);
    }

    private List<ResponseEntity<String>> concurrent(Supplier<ResponseEntity<String>> firstCall,
                                                     Supplier<ResponseEntity<String>> secondCall) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                users.findByEmailForUpdate(userA.getEmail()).orElseThrow();
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
                Future<ResponseEntity<String>> first = pool.submit(firstCall::get);
                Future<ResponseEntity<String>> second = pool.submit(secondCall::get);
                assertThrows(TimeoutException.class, () -> first.get(300, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    private String expiredToken() {
        return io.jsonwebtoken.Jwts.builder().subject(userA.getEmail())
                .expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(60)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        testJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
    }

    private UserEntity user(String email, Role role) {
        return users.save(UserEntity.builder().email(email).fullName("Customer")
                .password("unused-test-password").role(role).status(UserStatus.ACTIVE).build());
    }

    private String token(UserEntity user) { return jwt.generateToken(new CustomUserDetails(user)); }

    private ProductEntity product(String slug) {
        return products.save(ProductEntity.builder().name(slug).slug(slug).price(new BigDecimal("100000"))
                .discountPrice(new BigDecimal("90000")).thumbnailUrl("https://example.test/" + slug + ".png")
                .quantity(10).status(ProductStatus.ACTIVE).brand(brand).category(category).build());
    }

    private int stock() { return products.findById(product.getId()).orElseThrow().getQuantity(); }

    private void assertNoCommerceData() {
        assertThat(carts.count()).isZero();
        assertThat(cartItems.count()).isZero();
        assertThat(orders.count()).isZero();
        assertThat(orderItems.count()).isZero();
        assertThat(payments.count()).isZero();
    }

    private ResponseEntity<String> get(String auth, String query) { return call(HttpMethod.GET, "/api/wishlist" + query, auth, null); }
    private ResponseEntity<String> add(String auth, long id) { return call(HttpMethod.POST, "/api/wishlist/" + id, auth, null); }
    private ResponseEntity<String> remove(String auth, long id) { return call(HttpMethod.DELETE, "/api/wishlist/" + id, auth, null); }

    private ResponseEntity<String> call(HttpMethod method, String path, String auth, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth != null) headers.setBearerAuth(auth);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body.path("data");
    }

    private void error(ResponseEntity<String> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(status);
        assertThat(json.readTree(response.getBody()).path("success").asBoolean()).isFalse();
    }
}
