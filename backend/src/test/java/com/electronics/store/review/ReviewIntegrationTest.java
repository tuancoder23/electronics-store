package com.electronics.store.review;

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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Real HTTP, JWT authentication and committed transactions, with lazy loading outside services disabled.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:review_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false"
})
class ReviewIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired ReviewRepository reviews;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired PaymentRepository payments;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired WishlistItemRepository wishlist;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;

    private UserEntity userA;
    private UserEntity userB;
    private String tokenA;
    private String tokenB;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
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
        userA = user("a@example.test");
        userB = user("b@example.test");
        tokenA = token(userA);
        tokenB = token(userB);
        BrandEntity brand = brands.save(BrandEntity.builder().name("Acme").slug("acme").build());
        CategoryEntity category = categories.save(CategoryEntity.builder().name("Phones").slug("phones").build());
        product = products.save(ProductEntity.builder().name("Phone").slug("phone")
                .price(new BigDecimal("100000")).quantity(10).brand(brand).category(category).build());
    }

    @Test
    void publicEmptyReviewsAndSummaryRequireNoJwt() throws Exception {
        JsonNode page = data(getReviews(""), 200);
        assertThat(page.path("content")).isEmpty();
        assertThat(page.path("totalElements").asLong()).isZero();
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(12);
        summary(0.0, 0);
        assertThat(reviews.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void deliveredPurchaseCreatesTrimmedReviewWithSafeUserSummary(int rating) throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        JsonNode created = data(create(tokenA, rating, "  Excellent product  \n"), 201);
        assertThat(created.path("id").asLong()).isPositive();
        assertThat(created.path("rating").asInt()).isEqualTo(rating);
        assertThat(created.path("comment").asText()).isEqualTo("Excellent product");
        assertThat(created.path("user").size()).isEqualTo(2);
        assertThat(created.path("user").path("id").asLong()).isEqualTo(userA.getId());
        assertThat(created.path("user").path("fullName").asText()).isEqualTo(userA.getFullName());
        assertThat(created.path("createdAt").asText()).isNotBlank();
        assertThat(created.path("updatedAt")).isEqualTo(created.path("createdAt"));
        JsonNode listed = data(getReviews(""), 200).path("content").get(0);
        assertThat(listed).isEqualTo(created);
        for (String field : List.of("email", "phone", "password", "role", "authorities", "accessToken")) {
            assertThat(listed.findValues(field)).isEmpty();
        }
        assertThat(reviews.countByProductId(product.getId())).isEqualTo(1);
        summary(rating, 1);
        assertThat(products.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(payments.count()).isZero();
    }

    @Test
    void anotherUsersDeliveredOrderDoesNotAuthorizeReview() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        error(create(tokenB, 5, "Good"), 403, "You have not purchased this product.");
        assertThat(reviews.count()).isZero();
    }

    @Test
    void deliveredOrderForDifferentProductDoesNotAuthorizeReview() throws Exception {
        purchase(userA, Long.MAX_VALUE, OrderStatus.DELIVERED);
        error(create(tokenA, 5, "Good"), 403, "You have not purchased this product.");
        assertThat(reviews.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    void allNonDeliveredOrdersAreRejected(OrderStatus orderStatus) throws Exception {
        purchase(userA, product.getId(), orderStatus);
        error(create(tokenA, 5, "Good"), 403,
                "You can review this product only after your order is delivered.");
        assertThat(reviews.count()).isZero();
    }

    @Test
    void anyMatchingDeliveredOrderQualifiesEvenWithOtherPendingOrders() throws Exception {
        purchase(userA, product.getId(), OrderStatus.PENDING);
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        purchase(userA, product.getId(), OrderStatus.SHIPPING);
        data(create(tokenA, 5, "Good"), 201);
    }

    @Test
    void duplicateReturns409AndPreservesOriginalReview() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        JsonNode original = data(create(tokenA, 5, "Original"), 201);
        error(create(tokenA, 1, "Replacement"), 409, "You have already reviewed this product.");
        assertThat(data(getReviews(""), 200).path("content").get(0)).isEqualTo(original);
        assertThat(reviews.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 6})
    void invalidRatingRejectsCreateAndUpdateWithoutChangingData(Integer rating) throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        error(create(tokenA, rating, "Good"), 400, null);
        assertThat(reviews.count()).isZero();
        JsonNode original = data(create(tokenA, 5, "Original"), 201);
        error(update(tokenA, original.path("id").asLong(), rating, "Changed"), 400, null);
        assertThat(data(getReviews(""), 200).path("content").get(0)).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "5.9", "\"5\"", "true", "{}", "[]", "2147483648"})
    void ratingMustBeAJsonIntegerWithoutCoercion(String rating) throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        String body = "{\"rating\":" + rating + ",\"comment\":\"Good\"}";
        error(call(HttpMethod.POST, reviewPath(), tokenA, body), 400, null);
        assertThat(reviews.count()).isZero();
        long id = data(create(tokenA, 5, "Original"), 201).path("id").asLong();
        error(call(HttpMethod.PUT, "/api/reviews/" + id, tokenA, body), 400, null);
        assertThat(reviews.findById(id).orElseThrow().getRating()).isEqualTo(5);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n", "\u2003", "too-long"})
    void invalidCommentRejectsCreateAndUpdate(String comment) throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        String value = "too-long".equals(comment) ? "x".repeat(1001) : comment;
        error(create(tokenA, 5, value), 400, null);
        assertThat(reviews.count()).isZero();
        JsonNode original = data(create(tokenA, 5, "Original"), 201);
        error(update(tokenA, original.path("id").asLong(), 4, value), 400, null);
        assertThat(data(getReviews(""), 200).path("content").get(0)).isEqualTo(original);
    }

    @Test
    void commentLengthIsValidatedAfterTrimming() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        JsonNode created = data(create(tokenA, 5, " " + "x".repeat(1000) + " "), 201);
        assertThat(created.path("comment").asText()).hasSize(1000);
        data(update(tokenA, created.path("id").asLong(), 1, "x"), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "productId", "user", "product", "id", "createdAt", "updatedAt"})
    void requestCannotSetOwnershipOrImmutableFields(String field) throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        String body = "{\"rating\":5,\"comment\":\"Good\",\"" + field + "\":123}";
        error(call(HttpMethod.POST, reviewPath(), tokenA, body), 400, null);
        assertThat(reviews.count()).isZero();
        long id = data(create(tokenA, 5, "Original"), 201).path("id").asLong();
        error(call(HttpMethod.PUT, "/api/reviews/" + id, tokenA, body), 400, null);
        assertThat(reviews.findById(id).orElseThrow().getComment()).isEqualTo("Original");
    }

    @Test
    void updateOwnReviewPreservesIdentityAndCreationTimeAndRefreshesSummary() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        JsonNode original = data(create(tokenA, 5, "Original"), 201);
        long id = original.path("id").asLong();
        JsonNode changed = data(update(tokenA, id, 4, "  After using it  "), 200);
        assertThat(changed.path("id")).isEqualTo(original.path("id"));
        assertThat(changed.path("createdAt")).isEqualTo(original.path("createdAt"));
        assertThat(changed.path("user")).isEqualTo(original.path("user"));
        assertThat(LocalDateTime.parse(changed.path("updatedAt").asText()))
                .isAfter(LocalDateTime.parse(original.path("updatedAt").asText()));
        assertThat(changed.path("comment").asText()).isEqualTo("After using it");
        assertThat(reviews.findByUserIdAndProductId(userA.getId(), product.getId())).isPresent();
        summary(4.0, 1);
    }

    @Test
    void otherUserAndAdminCannotUpdateOrDeleteEvenWithSpoofedUserId() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        JsonNode original = data(create(tokenA, 5, "Original"), 201);
        long id = original.path("id").asLong();
        UserEntity admin = user("admin@example.test");
        admin.setRole(Role.ADMIN);
        users.saveAndFlush(admin);
        for (String auth : List.of(tokenB, token(admin))) {
            error(call(HttpMethod.PUT, "/api/reviews/" + id + "?userId=" + userA.getId(), auth,
                    body(1, "Changed")), 404, null);
            error(call(HttpMethod.DELETE, "/api/reviews/" + id + "?userId=" + userA.getId(), auth, null), 404, null);
        }
        assertThat(data(getReviews(""), 200).path("content").get(0)).isEqualTo(original);
        summary(5.0, 1);
    }

    @Test
    void deletingOwnReviewRecalculatesAverageAndCountAndAllowsNewReview() throws Exception {
        UserEntity third = user("third@example.test");
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        purchase(userB, product.getId(), OrderStatus.DELIVERED);
        purchase(third, product.getId(), OrderStatus.DELIVERED);
        long firstId = data(create(tokenA, 5, "Great"), 201).path("id").asLong();
        long secondId = data(create(tokenB, 4, "Good"), 201).path("id").asLong();
        long thirdId = data(create(token(third), 3, "Okay"), 201).path("id").asLong();
        summary(4.0, 3);
        data(remove(token(third), thirdId), 200);
        summary(4.5, 2);
        assertThat(reviews.existsById(thirdId)).isFalse();
        data(remove(tokenA, firstId), 200);
        data(remove(tokenB, secondId), 200);
        summary(0.0, 0);
        error(remove(tokenA, firstId), 404, null);
        data(create(tokenA, 1, "New review"), 201);
        summary(1.0, 1);
        assertThat(products.existsById(product.getId())).isTrue();
        assertThat(users.existsById(userA.getId())).isTrue();
    }

    @Test
    void paginationFiltersProductAndOrdersByDateThenIdDescending() throws Exception {
        UserEntity third = user("third@example.test");
        ReviewEntity first = persistReview(userA, product, 5);
        ReviewEntity second = persistReview(userB, product, 4);
        ReviewEntity last = persistReview(third, product, 3);
        ProductEntity other = products.save(ProductEntity.builder().name("Other").slug("other")
                .price(BigDecimal.ONE).quantity(1).brand(product.getBrand()).category(product.getCategory()).build());
        persistReview(userA, other, 1);
        jdbc.update("update reviews set created_at=?", LocalDateTime.of(2026, 1, 1, 0, 0));
        jdbc.update("update reviews set created_at=? where id=?", LocalDateTime.of(2026, 1, 2, 0, 0), first.getId());
        JsonNode page = data(getReviews("?page=0&size=2"), 200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("content")).hasSize(2);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(first.getId());
        assertThat(page.path("content").get(1).path("id").asLong()).isEqualTo(last.getId());
        JsonNode end = data(getReviews("?page=1&size=2"), 200);
        assertThat(end.path("content")).hasSize(1);
        assertThat(end.path("content").get(0).path("id").asLong()).isEqualTo(second.getId());
        assertThat(end.path("last").asBoolean()).isTrue();
        assertThat(data(getReviews("?page=2&size=2"), 200).path("content")).isEmpty();
        summary(4.0, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=abc", "?size=2147483648"})
    void invalidPaginationReturns400(String query) throws Exception {
        error(getReviews(query), 400, null);
    }

    @Test
    void missingProductAndReviewReturn404() throws Exception {
        error(call(HttpMethod.GET, "/api/products/" + Long.MAX_VALUE + "/reviews", null, null), 404, null);
        error(call(HttpMethod.GET, "/api/products/" + Long.MAX_VALUE + "/rating-summary", null, null), 404, null);
        error(call(HttpMethod.POST, "/api/products/" + Long.MAX_VALUE + "/reviews", tokenA, body(5, "Good")), 404, null);
        error(update(tokenA, Long.MAX_VALUE, 5, "Good"), 404, null);
        error(remove(tokenA, Long.MAX_VALUE), 404, null);
        assertThat(reviews.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidIdsReturn400(String id) throws Exception {
        error(call(HttpMethod.GET, "/api/products/" + id + "/reviews", null, null), 400, null);
        error(call(HttpMethod.GET, "/api/products/" + id + "/rating-summary", null, null), 400, null);
        error(call(HttpMethod.POST, "/api/products/" + id + "/reviews", tokenA, body(5, "Good")), 400, null);
        error(call(HttpMethod.PUT, "/api/reviews/" + id, tokenA, body(5, "Good")), 400, null);
        error(call(HttpMethod.DELETE, "/api/reviews/" + id, tokenA, null), 400, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid", "inactive"})
    void allWritesRequireValidActiveJwt(String mode) throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        long id = data(create(tokenA, 5, "Original"), 201).path("id").asLong();
        String auth = switch (mode) {
            case "missing" -> null;
            case "invalid" -> "invalid-token";
            case "inactive" -> {
                userA.setStatus(UserStatus.INACTIVE);
                users.saveAndFlush(userA);
                yield tokenA;
            }
            default -> throw new AssertionError(mode);
        };
        error(create(auth, 1, "Changed"), 401, null);
        error(update(auth, id, 1, "Changed"), 401, null);
        error(remove(auth, id), 401, null);
        assertThat(reviews.findById(id).orElseThrow().getRating()).isEqualTo(5);
    }

    @Test
    void databaseRejectsDuplicateAndInvalidRatingWhenServiceIsBypassed() {
        ReviewEntity existing = persistReview(userA, product, 5);
        assertThrows(DataIntegrityViolationException.class, () -> persistReview(userA, product, 4));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update reviews set rating=0 where id=?", existing.getId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update reviews set rating=6 where id=?", existing.getId()));
        assertThat(reviews.count()).isEqualTo(1);
        assertThat(reviews.findById(existing.getId()).orElseThrow().getRating()).isEqualTo(5);
    }

    @Test
    void persistenceFailureRollsBackAndRetrySucceeds() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        jdbc.execute("alter table reviews add constraint test_review_failure check (product_id < 0)");
        try {
            error(create(tokenA, 5, "Good"), 500, "An unexpected error occurred");
            assertThat(reviews.count()).isZero();
        } finally {
            jdbc.execute("alter table reviews drop constraint test_review_failure");
        }
        data(create(tokenA, 5, "Good"), 201);
    }

    @Test
    void concurrentCreatesReturnOne201AndOne409() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
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
                Future<ResponseEntity<String>> first = pool.submit(() -> create(tokenA, 5, "First"));
                Future<ResponseEntity<String>> second = pool.submit(() -> create(tokenA, 4, "Second"));
                assertThrows(TimeoutException.class, () -> first.get(300, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                List<ResponseEntity<String>> responses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                assertThat(responses.stream().map(r -> r.getStatusCode().value())).containsExactlyInAnyOrder(201, 409);
                for (ResponseEntity<String> response : responses) {
                    if (response.getStatusCode().value() == 409) {
                        error(response, 409, "You have already reviewed this product.");
                    }
                }
                assertThat(reviews.count()).isEqualTo(1);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void checkoutAndAdminDeliveryUnlockReviewWithoutChangingCommerceData() throws Exception {
        userB.setRole(Role.ADMIN);
        users.saveAndFlush(userB);
        String admin = token(userB);
        data(call(HttpMethod.POST, "/api/cart/items", tokenA,
                "{\"productId\":" + product.getId() + ",\"quantity\":1}"), 201);
        long orderId = data(call(HttpMethod.POST, "/api/orders", tokenA, """
                {"receiverName":"Customer","phone":"0901234567","shippingAddress":"Test street","paymentMethod":"COD"}
                """), 201).path("id").asLong();
        for (String state : List.of("PENDING", "CONFIRMED", "SHIPPING", "DELIVERED")) {
            if (!state.equals("PENDING")) {
                data(call(HttpMethod.PUT, "/api/admin/orders/" + orderId + "/status", admin,
                        "{\"status\":\"" + state + "\"}"), 200);
            }
            if (!state.equals("DELIVERED")) error(create(tokenA, 5, "Good"), 403, null);
        }
        JsonNode before = data(call(HttpMethod.GET, "/api/orders/" + orderId, tokenA, null), 200);
        long id = data(create(tokenA, 5, "Good"), 201).path("id").asLong();
        data(update(tokenA, id, 4, "Updated"), 200);
        data(remove(tokenA, id), 200);
        assertThat(data(call(HttpMethod.GET, "/api/orders/" + orderId, tokenA, null), 200)).isEqualTo(before);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(cartItems.count()).isZero();
        assertThat(products.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(9);
    }

    @Test
    void productHardDeletionCleansReviewsAndPreservesHistoricalOrders() throws Exception {
        purchase(userA, product.getId(), OrderStatus.DELIVERED);
        data(create(tokenA, 5, "Good"), 201);
        userB.setRole(Role.ADMIN);
        users.saveAndFlush(userB);
        data(call(HttpMethod.DELETE, "/api/admin/products/" + product.getId(), token(userB), null), 200);
        assertThat(reviews.count()).isZero();
        assertThat(orders.count()).isEqualTo(1);
        assertThat(orderItems.count()).isEqualTo(1);
        assertThat(users.existsById(userA.getId())).isTrue();
    }

    private UserEntity user(String email) {
        return users.save(UserEntity.builder().email(email).fullName("Customer")
                .password("unused-test-password").role(Role.USER).status(UserStatus.ACTIVE).build());
    }

    private String token(UserEntity user) { return jwt.generateToken(new CustomUserDetails(user)); }

    private void purchase(UserEntity buyer, Long productId, OrderStatus status) {
        OrderEntity order = OrderEntity.builder().user(buyer).receiverName("Customer").phone("0901234567")
                .shippingAddress("Test street").subtotal(BigDecimal.TEN).totalAmount(BigDecimal.TEN)
                .status(status).paymentMethod(PaymentMethod.COD).build();
        order.addItem(OrderItemEntity.builder().productId(productId).productName("Purchased product")
                .unitPrice(BigDecimal.TEN).quantity(1).lineTotal(BigDecimal.TEN).build());
        orders.saveAndFlush(order);
    }

    private ReviewEntity persistReview(UserEntity user, ProductEntity target, int rating) {
        return reviews.saveAndFlush(ReviewEntity.builder().user(user).product(target).rating(rating).comment("Good").build());
    }

    private void summary(double average, long count) throws Exception {
        JsonNode result = data(call(HttpMethod.GET, "/api/products/" + product.getId() + "/rating-summary", null, null), 200);
        assertThat(result.path("averageRating").asDouble()).isEqualTo(average);
        assertThat(result.path("reviewCount").asLong()).isEqualTo(count);
    }

    private String reviewPath() { return "/api/products/" + product.getId() + "/reviews"; }
    private ResponseEntity<String> getReviews(String query) { return call(HttpMethod.GET, reviewPath() + query, null, null); }
    private ResponseEntity<String> create(String auth, Integer rating, String comment) throws Exception {
        return call(HttpMethod.POST, reviewPath(), auth, body(rating, comment));
    }
    private ResponseEntity<String> update(String auth, long id, Integer rating, String comment) throws Exception {
        return call(HttpMethod.PUT, "/api/reviews/" + id, auth, body(rating, comment));
    }
    private ResponseEntity<String> remove(String auth, long id) { return call(HttpMethod.DELETE, "/api/reviews/" + id, auth, null); }

    private String body(Integer rating, String comment) throws Exception {
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("rating", rating);
        fields.put("comment", comment);
        return json.writeValueAsString(fields);
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String auth, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth != null) headers.setBearerAuth(auth);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(status);
        JsonNode result = json.readTree(response.getBody());
        assertThat(result.path("success").asBoolean()).isTrue();
        return result.path("data");
    }

    private void error(ResponseEntity<String> response, int status, String message) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(status);
        JsonNode result = json.readTree(response.getBody());
        assertThat(result.path("success").asBoolean()).isFalse();
        if (message != null) assertThat(result.path("message").asText()).isEqualTo(message);
    }
}
