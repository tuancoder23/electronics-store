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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Local fake merchant configuration only. No outgoing request to VNPAY, no real credentials.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:vnpay_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false", "app.vnpay.enabled=true", "app.vnpay.tmn-code=TESTONLY",
        "app.vnpay.hash-secret=test-only-vnpay-secret",
        "app.vnpay.payment-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.vnpay.return-url=https://merchant.example/api/payments/vnpay/return",
        "app.vnpay.ipn-url=https://merchant.example/api/payments/vnpay/ipn"
})
class VnPayIntegrationTest {
    private static final String TEST_SECRET = "test-only-vnpay-secret";
    private static final String BASE = "/api/payments/vnpay";
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;

    private ProductEntity product;
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
        product = products.save(ProductEntity.builder().name("Phone").slug("phone").price(new BigDecimal("1000000"))
                .quantity(10).status(ProductStatus.ACTIVE).category(category).brand(brand).build());
    }

    @Test
    void checkoutAndCreateUrlUseServerAmountSandboxAndStableReference() throws Exception {
        long id = createOrder("VNPAY");
        assertThat(payment(id).getMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment(id).getAmount()).isEqualByComparingTo("2000000");
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusSeconds(2);
        JsonNode created = data(createUrl(id, tokenA), 200);
        String url = created.path("paymentUrl").asText();
        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).doesNotContain(TEST_SECRET, "vnp_SecureHashType", "vnp_IpnUrl");
        Map<String, String> query = parseUrl(url);
        assertThat(query.get("vnp_Amount")).isEqualTo("200000000");
        assertThat(query.get("vnp_Version")).isEqualTo("2.1.0");
        assertThat(query.get("vnp_Command")).isEqualTo("pay");
        assertThat(query.get("vnp_TmnCode")).isEqualTo("TESTONLY");
        assertThat(query.get("vnp_CurrCode")).isEqualTo("VND");
        assertThat(query.get("vnp_TxnRef")).isEqualTo(payment(id).getGatewayReference()).matches("[A-Za-z0-9]{32}");
        assertThat(query.get("vnp_SecureHash")).isEqualTo(testSign(query));
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime createdAt = LocalDateTime.parse(query.get("vnp_CreateDate"), format);
        assertThat(createdAt).isAfter(before).isBefore(before.plusMinutes(1));
        assertThat(LocalDateTime.parse(query.get("vnp_ExpireDate"), format)).isEqualTo(createdAt.plusMinutes(15));
        assertThat(data(createUrl(id, tokenA), 200).path("paymentUrl").asText()).isEqualTo(url);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(stock()).isEqualTo(8);
        assertThat(cartItems.count()).isZero();
        JsonNode detail = data(call(HttpMethod.GET, "/api/orders/" + id, tokenA, null), 200);
        assertThat(detail.findValues("gatewayPaymentUrl")).isEmpty();
        assertThat(detail.findValues("gatewayReference")).isEmpty();
    }

    @Test
    void createRequiresJwtOwnershipAndIgnoresClientAmountUserAndForwardedIp() throws Exception {
        long id = createOrder("VNPAY");
        for (String token : new String[]{null, "invalid", tokenB, adminToken}) {
            error(createUrl(id, token), token == null || token.equals("invalid") ? 401 : 404);
        }
        error(createUrl(Long.MAX_VALUE, tokenA), 404);
        assertThat(payment(id).getGatewayReference()).isNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "1.2.3.4");
        headers.set("Forwarded", "for=1.2.3.4");
        JsonNode response = data(http.exchange(BASE + "/create/" + id + "?amount=1&userId=999", HttpMethod.POST,
                new HttpEntity<>("{\"amount\":1,\"userId\":999}", headers), String.class), 200);
        Map<String, String> fields = parseUrl(response.path("paymentUrl").asText());
        assertThat(fields.get("vnp_Amount")).isEqualTo("200000000");
        assertThat(fields.get("vnp_IpAddr")).isNotBlank().isNotEqualTo("1.2.3.4");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void terminalPaymentCannotCreateNewUrl(PaymentStatus status) throws Exception {
        long id = createOrder("VNPAY");
        jdbc.update("update payments set status=? where order_id=?", status.name(), id);
        error(createUrl(id, tokenA), 400);
        assertThat(payment(id).getGatewayReference()).isNull();
        assertThat(payments.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "DELIVERED"})
    void terminalOrderCannotCreateUrl(OrderStatus status) throws Exception {
        long id = createOrder("VNPAY");
        jdbc.update("update orders set status=? where id=?", status.name(), id);
        error(createUrl(id, tokenA), 400);
    }

    @Test
    void codAndMissingPaymentCannotCreateVnpayUrl() throws Exception {
        long id = createOrder("COD");
        error(createUrl(id, tokenA), 400);
        long other = createOrder("VNPAY");
        jdbc.update("delete from payments where order_id=?", other);
        error(createUrl(other, tokenA), 404);
    }

    @Test
    void expiredUrlDoesNotRotateReferenceOrCreateAnotherPayment() throws Exception {
        long id = initializedOrder();
        String reference = payment(id).getGatewayReference();
        jdbc.update("update payments set gateway_expires_at=? where order_id=?", LocalDateTime.of(2000, 1, 1, 0, 0), id);
        error(createUrl(id, tokenA), 400);
        assertThat(payment(id).getGatewayReference()).isEqualTo(reference);
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void validIpnAfterUrlExpiryStillConfirmsPayment() throws Exception {
        long id = initializedOrder();
        jdbc.update("update payments set gateway_expires_at=? where order_id=?", LocalDateTime.of(2000, 1, 1, 0, 0), id);
        assertIpn(callbackCall("/ipn", callback(id, "00", "00"), null, null), "00");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20260230123000", "202609091230", "20260909243000", "+100000909123000", "-00010909123000"})
    void invalidPaymentDatesAreRejectedByBothCallbacksWithoutMutation(String date) throws Exception {
        long id = initializedOrder();
        PaymentEntity before = payment(id);
        Map<String, String> fields = callback(id, "00", "00");
        fields.put("vnp_PayDate", date);
        error(callbackCall("/return", fields, null, null), 400);
        assertIpn(callbackCall("/ipn", fields, null, null), "99");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment(id).getPaidAt()).isNull();
        assertThat(payment(id).getTransactionCode()).isNull();
        assertThat(payment(id).getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void optionalPaymentDateFallsBackToGatewayTimezone(boolean empty) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        if (empty) fields.put("vnp_PayDate", "");
        else fields.remove("vnp_PayDate");
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusSeconds(1);
        data(callbackCall("/return", fields, null, null), 200);
        assertThat(payment(id).getPaidAt()).isNull();
        assertIpn(callbackCall("/ipn", fields, null, null), "00");
        assertThat(payment(id).getPaidAt()).isBetween(before,
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusSeconds(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "000000000000000"})
    void successWithoutRealTransactionNumberIsRejectedByBothCallbacks(String transactionNo) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        fields.put("vnp_TransactionNo", transactionNo);
        error(callbackCall("/return", fields, null, null), 400);
        assertIpn(callbackCall("/ipn", fields, null, null), "99");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment(id).getTransactionCode()).isNull();
    }

    @Test
    void returnVerifiesResultButOnlyIpnMarksPaidWithoutOrderOrCartSideEffects() throws Exception {
        long id = initializedOrder();
        add(tokenA, 1);
        Map<String, String> fields = callback(id, "00", "00");
        JsonNode browser = data(callbackCall("/return", fields, "invalid", null), 200);
        assertThat(browser.path("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertIpn(callbackCall("/ipn", fields, "invalid", null), "00");
        PaymentEntity paid = payment(id);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getTransactionCode()).isEqualTo("123456789");
        assertThat(paid.getPaidAt()).isEqualTo(LocalDateTime.of(2026, 9, 9, 12, 30));
        assertThat(orderStatus(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock()).isEqualTo(8);
        assertThat(cartItems.count()).isEqualTo(1);
        assertThat(orders.count()).isEqualTo(1);
        error(createUrl(id, tokenA), 400);
        error(cancel(id), 400);
        error(adminUpdate(id, "CANCELLED"), 400);
        assertThat(orderStatus(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock()).isEqualTo(8);
        assertIpn(callbackCall("/ipn", fields, null, null), "02");
        assertThat(payment(id).getPaidAt()).isEqualTo(paid.getPaidAt());
        assertThat(payment(id).getUpdatedAt()).isEqualTo(paid.getUpdatedAt());
        assertThat(data(callbackCall("/return", fields, null, null), 200).path("paymentStatus").asText()).isEqualTo("PAID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid", "tampered", "duplicate"})
    void invalidSignaturesAndDuplicateParametersNeverMutatePayment(String mode) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        fields.put("vnp_SecureHash", testSign(fields));
        if (mode.equals("missing")) fields.remove("vnp_SecureHash");
        if (mode.equals("invalid")) fields.put("vnp_SecureHash", "0".repeat(128));
        if (mode.equals("tampered")) fields.put("vnp_Amount", "1");
        String extra = mode.equals("duplicate") ? "&vnp_Amount=200000000" : "";
        assertIpn(rawCallback("/ipn", fields, null, extra), "97");
        error(rawCallback("/return", fields, null, extra), 400);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void signedWrongAmountReferenceAndMerchantAreRejected() throws Exception {
        long id = initializedOrder();
        for (String key : List.of("vnp_Amount", "vnp_TxnRef", "vnp_TmnCode")) {
            Map<String, String> fields = callback(id, "00", "00");
            fields.put(key, key.equals("vnp_Amount") ? "100" : "UNKNOWN1");
            assertIpn(callbackCall("/ipn", fields, null, null), key.equals("vnp_Amount") ? "04" : "01");
            error(callbackCall("/return", fields, null, null), 400);
        }
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment(id).getPaidAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-200000000", "200000000.00", "2e8", "1000000000000", ""})
    void signedMalformedAmountsNeverMutatePayment(String amount) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        fields.put("vnp_Amount", amount);
        assertIpn(callbackCall("/ipn", fields, null, null), "04");
        error(callbackCall("/return", fields, null, null), 400);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "paymentMethod", "orderMethod"})
    void inconsistentStoredPaymentIsRejectedByCreateAndCallbacks(String mismatch) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        switch (mismatch) {
            case "amount" -> jdbc.update("update payments set amount=1 where order_id=?", id);
            case "paymentMethod" -> jdbc.update("update payments set method='COD' where order_id=?", id);
            case "orderMethod" -> jdbc.update("update orders set payment_method='COD' where id=?", id);
            default -> throw new AssertionError("Unknown mismatch");
        }
        error(createUrl(id, tokenA), 400);
        error(callbackCall("/return", fields, null, null), 400);
        assertIpn(callbackCall("/ipn", fields, null, null), mismatch.equals("amount") ? "04" : "01");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void callbackJwtExemptionDoesNotExposeOtherMethodsOrRoutes() throws Exception {
        long id = initializedOrder();
        for (String endpoint : List.of("/return", "/ipn")) {
            error(call(HttpMethod.POST, BASE + endpoint, null, null), 401);
            error(call(HttpMethod.POST, BASE + endpoint, "invalid", null), 401);
        }
        error(call(HttpMethod.GET, BASE + "/create/" + id, "invalid", null), 401);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vnp_ResponseCode", "vnp_TransactionStatus", "vnp_TransactionNo", "vnp_BankCode", "vnp_OrderInfo"})
    void signedMissingRequiredCallbackFieldsAreRejected(String key) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        fields.remove(key);
        assertIpn(callbackCall("/ipn", fields, null, null), "99");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"24", "51", "11", "99"})
    void confirmedFailureChangesOnlyPaymentAndDoesNotCancelOrder(String code) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, code, "02");
        fields.put("vnp_TransactionNo", "0");
        data(callbackCall("/return", fields, null, null), 200);
        assertIpn(callbackCall("/ipn", fields, null, null), "00");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment(id).getTransactionCode()).isEqualTo("0");
        assertThat(payment(id).getPaidAt()).isNull();
        assertThat(orderStatus(id)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock()).isEqualTo(8);
        assertIpn(callbackCall("/ipn", fields, null, null), "02");
        assertIpn(callbackCall("/ipn", callback(id, "00", "00"), null, null), "02");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.FAILED);
        data(cancel(id), 200);
        assertThat(stock()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:02", "24:00", "07:07", "99:01", "99:04", "99:05"})
    void ambiguousOrUnfinishedResultsStayPending(String result) throws Exception {
        long id = initializedOrder();
        String[] codes = result.split(":");
        assertIpn(callbackCall("/ipn", callback(id, codes[0], codes[1]), null, null), "99");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment(id).getPaidAt()).isNull();
    }

    @Test
    void lateSuccessAfterOrderCancellationDoesNotRestoreOrChargeStockAgain() throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        data(cancel(id), 200);
        assertIpn(callbackCall("/ipn", fields, null, null), "02");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderStatus(id)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void ipnDatabaseFailureRollsBackAndReturnsRetryProtocol() throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        jdbc.execute("alter table payments add constraint test_vnpay_ipn_failure check (status <> 'PAID')");
        try {
            assertIpn(callbackCall("/ipn", fields, null, null), "99");
            assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment(id).getTransactionCode()).isNull();
            assertThat(payment(id).getPaidAt()).isNull();
            assertThat(stock()).isEqualTo(8);
        } finally {
            jdbc.execute("alter table payments drop constraint test_vnpay_ipn_failure");
        }
        assertIpn(callbackCall("/ipn", fields, null, null), "00");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void urlPersistenceFailureRollsBackReferenceAndRetryCreatesOneUrl() throws Exception {
        long id = createOrder("VNPAY");
        jdbc.execute("alter table payments add constraint test_vnpay_url_failure check (gateway_reference is null)");
        try {
            error(createUrl(id, tokenA), 500);
            assertThat(payment(id).getGatewayReference()).isNull();
            assertThat(payment(id).getGatewayPaymentUrl()).isNull();
        } finally {
            jdbc.execute("alter table payments drop constraint test_vnpay_url_failure");
        }
        data(createUrl(id, tokenA), 200);
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void vnpayRejectsZeroAmountAndRollsBackCheckoutWhileCodStillAcceptsZero() throws Exception {
        product.setPrice(BigDecimal.ZERO);
        products.saveAndFlush(product);
        add(tokenA, 2);
        error(call(HttpMethod.POST, "/api/orders", tokenA, checkoutBody("VNPAY")), 400);
        assertThat(orders.count()).isZero();
        assertThat(payments.count()).isZero();
        assertThat(stock()).isEqualTo(10);
        assertThat(cartItems.count()).isEqualTo(1);
        data(call(HttpMethod.POST, "/api/orders", tokenA, checkoutBody("COD")), 201);
    }

    @Test
    void codDeliveryStillMarksPaidWithSandboxEnabled() throws Exception {
        long id = createOrder("COD");
        for (String next : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) data(adminUpdate(id, next), 200);
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment(id).getPaidAt()).isNotNull();
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void concurrentUrlCreationReturnsSameUrlAndConcurrentIpnsApplyOnce() throws Exception {
        long id = createOrder("VNPAY");
        List<ResponseEntity<String>> created = concurrent(id, () -> createUrl(id, tokenA), () -> createUrl(id, tokenA));
        assertThat(data(created.get(0), 200).path("paymentUrl")).isEqualTo(data(created.get(1), 200).path("paymentUrl"));
        Map<String, String> fields = callback(id, "00", "00");
        List<ResponseEntity<String>> results = concurrent(id, () -> callbackCall("/ipn", fields, null, null),
                () -> callbackCall("/ipn", fields, null, null));
        assertThat(List.of(json.readTree(results.get(0).getBody()).path("RspCode").asText(),
                json.readTree(results.get(1).getBody()).path("RspCode").asText())).containsExactlyInAnyOrder("00", "02");
        assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(stock()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentCancellationAndIpnRespectOrderLock(boolean admin) throws Exception {
        long id = initializedOrder();
        Map<String, String> fields = callback(id, "00", "00");
        List<ResponseEntity<String>> result = concurrent(id, () -> admin ? adminUpdate(id, "CANCELLED") : cancel(id),
                () -> callbackCall("/ipn", fields, null, null));
        if (result.get(0).getStatusCode().value() == 200) {
            assertIpn(result.get(1), "02");
            assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(stock()).isEqualTo(10);
        } else {
            error(result.get(0), 400);
            assertIpn(result.get(1), "00");
            assertThat(payment(id).getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(orderStatus(id)).isEqualTo(OrderStatus.PENDING);
            assertThat(stock()).isEqualTo(8);
        }
    }

    private List<ResponseEntity<String>> concurrent(long id, Supplier<ResponseEntity<String>> firstCall,
                                                     Supplier<ResponseEntity<String>> secondCall) throws Exception {
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
                Future<ResponseEntity<String>> first = pool.submit(firstCall::get);
                Future<ResponseEntity<String>> second = pool.submit(secondCall::get);
                try {
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

    private Map<String, String> callback(long id, String code, String status) {
        return new HashMap<>(Map.of("vnp_TmnCode", "TESTONLY", "vnp_Amount", "200000000",
                "vnp_TxnRef", payment(id).getGatewayReference(), "vnp_ResponseCode", code,
                "vnp_TransactionStatus", status, "vnp_TransactionNo", "123456789", "vnp_BankCode", "NCB",
                "vnp_OrderInfo", "Thanh toan don hang " + id, "vnp_PayDate", "20260909123000"));
    }

    private ResponseEntity<String> callbackCall(String endpoint, Map<String, String> fields, String token, String extra) {
        Map<String, String> signed = new HashMap<>(fields);
        signed.put("vnp_SecureHash", testSign(signed));
        return rawCallback(endpoint, signed, token, extra == null ? "" : extra);
    }

    private ResponseEntity<String> rawCallback(String endpoint, Map<String, String> fields, String token, String extra) {
        String query = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(URI.create(http.getRootUri() + BASE + endpoint + "?" + query + extra), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    // Independent callback producer: deliberately does not call production VnPaySigner.
    private String testSign(Map<String, String> fields) {
        try {
            String canonical = new TreeMap<>(fields).entrySet().stream()
                    .filter(e -> e.getKey().startsWith("vnp_") && !Set.of("vnp_SecureHash", "vnp_SecureHashType").contains(e.getKey()))
                    .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, String> parseUrl(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private PaymentEntity payment(long id) { return payments.findByOrderId(id).orElseThrow(); }
    private OrderStatus orderStatus(long id) { return orders.findById(id).orElseThrow().getStatus(); }
    private int stock() { return products.findById(product.getId()).orElseThrow().getQuantity(); }

    private String token(String email, Role role) {
        UserEntity user = users.save(UserEntity.builder().fullName("Customer").email(email)
                .password("unused-test-password").role(role).status(UserStatus.ACTIVE).build());
        return jwt.generateToken(new CustomUserDetails(user));
    }

    private void add(String token, int quantity) throws Exception {
        data(call(HttpMethod.POST, "/api/cart/items", token,
                json.writeValueAsString(Map.of("productId", product.getId(), "quantity", quantity))), 201);
    }

    private String checkoutBody(String method) {
        return "{\"receiverName\":\"Customer\",\"phone\":\"0901234567\",\"shippingAddress\":\"Test street\",\"paymentMethod\":\""
                + method + "\"}";
    }

    private long createOrder(String method) throws Exception {
        add(tokenA, 2);
        return data(call(HttpMethod.POST, "/api/orders", tokenA, checkoutBody(method)), 201).path("id").asLong();
    }

    private long initializedOrder() throws Exception {
        long id = createOrder("VNPAY");
        data(createUrl(id, tokenA), 200);
        return id;
    }

    private ResponseEntity<String> createUrl(long id, String token) { return call(HttpMethod.POST, BASE + "/create/" + id, token, null); }
    private ResponseEntity<String> cancel(long id) { return call(HttpMethod.PUT, "/api/orders/" + id + "/cancel", tokenA, null); }
    private ResponseEntity<String> adminUpdate(long id, String status) {
        return call(HttpMethod.PUT, "/api/admin/orders/" + id + "/status", adminToken, "{\"status\":\"" + status + "\"}");
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(status);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body.path("data");
    }

    private void error(ResponseEntity<String> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(status);
        assertThat(json.readTree(response.getBody()).path("success").asBoolean()).isFalse();
    }

    private void assertIpn(ResponseEntity<String> response, String code) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("RspCode").asText()).isEqualTo(code);
        assertThat(body.path("Message").asText()).isNotBlank();
        assertThat(body.has("success")).isFalse();
    }
}
