package com.electronics.store.user;

import com.electronics.store.dto.request.ChangePasswordRequest;
import com.electronics.store.entity.Role;
import com.electronics.store.entity.UserEntity;
import com.electronics.store.entity.UserStatus;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.security.CustomUserDetails;
import com.electronics.store.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Real HTTP/JWT with committed writes; this class does not wrap tests in a transaction.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:user_profile_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.open-in-view=false",
        "logging.level.org.springframework.web.servlet=DEBUG"
})
@ExtendWith(OutputCaptureExtension.class)
class UserProfileIntegrationTest {
    private static final String ME = "/api/users/me";
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transaction;
    @LocalServerPort int port;

    private UserEntity userA;
    private UserEntity userB;
    private String tokenA;
    private String tokenB;
    private String oldPassword;
    private String newPassword;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        oldPassword = UUID.randomUUID().toString();
        newPassword = UUID.randomUUID().toString();
        String hash = passwordEncoder.encode(oldPassword);
        userA = users.save(user("Alice", "alice@example.com", hash));
        userB = users.save(user("Bob", "bob@example.com", hash));
        // Compare timestamps as stored by the database (microsecond precision).
        userA = users.findById(userA.getId()).orElseThrow();
        tokenA = data(login(userA.getEmail(), oldPassword), 200).path("accessToken").asText();
        tokenB = jwtService.generateToken(new CustomUserDetails(userB));
    }

    @Test
    void getProfileReturnsOnlySafeCurrentUserFields() throws Exception {
        JsonNode result = data(call(HttpMethod.GET, ME, tokenA, null), 200);
        assertThat(result.path("id").asLong()).isEqualTo(userA.getId());
        assertThat(result.path("fullName").asText()).isEqualTo("Alice");
        assertThat(result.path("email").asText()).isEqualTo("alice@example.com");
        assertThat(result.path("phone").asText()).isEqualTo("0901234567");
        assertThat(result.path("role").asText()).isEqualTo("USER");
        assertThat(result.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(result.path("createdAt").asText()).isNotBlank();
        assertThat(result.path("updatedAt").asText()).isNotBlank();
        assertSafe(result.toString());
    }

    @Test
    void allProfileRoutesRequireValidJwt() throws Exception {
        for (String token : new String[]{null, "invalid"}) {
            error(call(HttpMethod.GET, ME, token, null), 401);
            error(call(HttpMethod.PUT, ME, token, profile("Updated", "0907654321")), 401);
            error(call(HttpMethod.PUT, ME + "/password", token, passwordBody(oldPassword, newPassword, newPassword)), 401);
        }
        assertUnchanged();
    }

    @Test
    void inactiveUserCannotUsePreviouslyIssuedJwt() throws Exception {
        userA.setStatus(UserStatus.INACTIVE);
        users.saveAndFlush(userA);
        error(call(HttpMethod.GET, ME, tokenA, null), 401);
        error(call(HttpMethod.PUT, ME, tokenA, profile("Updated", null)), 401);
        error(call(HttpMethod.PUT, ME + "/password", tokenA, passwordBody(oldPassword, newPassword, newPassword)), 401);
    }

    @Test
    void updateProfileNormalizesValuesAndPreservesProtectedFields() throws Exception {
        JsonNode updated = data(call(HttpMethod.PUT, ME, tokenA, profile("  Alice Updated  ", " +84 (90) 123-4567 ")), 200);
        assertThat(updated.path("fullName").asText()).isEqualTo("Alice Updated");
        assertThat(updated.path("phone").asText()).isEqualTo("+84 (90) 123-4567");
        UserEntity saved = savedA();
        assertThat(saved.getEmail()).isEqualTo(userA.getEmail());
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getPassword()).isEqualTo(userA.getPassword());
        assertThat(saved.getCreatedAt()).isEqualTo(userA.getCreatedAt());
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(userA.getUpdatedAt());
        assertThat(data(call(HttpMethod.GET, ME, tokenA, null), 200).path("fullName").asText()).isEqualTo("Alice Updated");
        assertThat(users.findById(userB.getId()).orElseThrow().getFullName()).isEqualTo("Bob");
        assertSafe(updated.toString());
    }

    @Test
    void phoneCanBeClearedWithNullEmptyOrOmitted() throws Exception {
        for (String phone : new String[]{null, ""}) {
            data(call(HttpMethod.PUT, ME, tokenA, profile("Alice", phone)), 200);
            assertThat(savedA().getPhone()).isNull();
        }
        data(call(HttpMethod.PUT, ME, tokenA, Map.of("fullName", "Alice")), 200);
        assertThat(savedA().getPhone()).isNull();
    }

    @Test
    void profileAcceptsMaximumNameAndPhoneLengths() throws Exception {
        data(call(HttpMethod.PUT, ME, tokenA, profile("A".repeat(150), "1".repeat(30))), 200);
        assertThat(savedA().getFullName()).hasSize(150);
        assertThat(savedA().getPhone()).hasSize(30);
    }

    static Stream<String> invalidNames() {
        return Stream.of(null, "", "   ", "A".repeat(151));
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void invalidNameDoesNotUpdateProfile(String name) throws Exception {
        error(call(HttpMethod.PUT, ME, tokenA, profile(name, "0907654321")), 400);
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefghi", "123", "1234567890123456789012345678901", "0901234567<script>"})
    void invalidPhoneDoesNotUpdateNameEither(String phone) throws Exception {
        error(call(HttpMethod.PUT, ME, tokenA, profile("Changed", phone)), 400);
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "userId", "email", "role", "status", "password", "passwordHash"})
    void profileRejectsProtectedFields(String field) throws Exception {
        Map<String, Object> body = new HashMap<>(profile("Changed", "0907654321"));
        body.put(field, field.equals("userId") || field.equals("id") ? userB.getId() : "ADMIN");
        error(call(HttpMethod.PUT, ME, tokenA, body), 400);
        assertUnchanged();
        error(call(HttpMethod.GET, "/api/admin/orders", tokenA, null), 403);
    }

    @Test
    void queryUserIdCannotSelectAnotherUser() throws Exception {
        String query = "?userId=" + userB.getId();
        assertThat(data(call(HttpMethod.GET, ME + query, tokenA, null), 200).path("id").asLong()).isEqualTo(userA.getId());
        data(call(HttpMethod.PUT, ME + query, tokenA, profile("Alice Updated", null)), 200);
        data(call(HttpMethod.PUT, ME + "/password" + query, tokenA, passwordBody(oldPassword, newPassword, newPassword)), 200);
        UserEntity other = users.findById(userB.getId()).orElseThrow();
        assertThat(other.getFullName()).isEqualTo("Bob");
        assertThat(other.getPassword()).isEqualTo(userB.getPassword());
        assertThat(data(call(HttpMethod.GET, ME, tokenB, null), 200).path("id").asLong()).isEqualTo(userB.getId());
    }

    @Test
    void changePasswordStoresHashAndLoginUsesNewPassword() throws Exception {
        JsonNode response = data(call(HttpMethod.PUT, ME + "/password", tokenA,
                passwordBody(oldPassword, newPassword, newPassword)), 200);
        assertThat(response.isMissingNode() || response.isNull()).isTrue();
        UserEntity saved = savedA();
        assertThat(saved.getPassword()).isNotEqualTo(oldPassword).isNotEqualTo(newPassword).isNotEqualTo(userA.getPassword());
        assertThat(passwordEncoder.matches(newPassword, saved.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(oldPassword, saved.getPassword())).isFalse();
        assertThat(saved.getFullName()).isEqualTo("Alice");
        assertThat(saved.getPhone()).isEqualTo(userA.getPhone());
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(users.findById(userB.getId()).orElseThrow().getPassword()).isEqualTo(userB.getPassword());
        error(login(userA.getEmail(), oldPassword), 401);
        String newToken = data(login(userA.getEmail(), newPassword), 200).path("accessToken").asText();
        assertThat(data(call(HttpMethod.GET, ME, newToken, null), 200).path("id").asLong()).isEqualTo(userA.getId());
        // Existing stateless JWTs retain their normal expiry; this feature adds no token revocation.
        data(call(HttpMethod.GET, ME, tokenA, null), 200);
    }

    @Test
    void wrongCurrentPasswordReturnsClearErrorAndPreservesHash() throws Exception {
        ResponseEntity<String> response = call(HttpMethod.PUT, ME + "/password", tokenA,
                passwordBody(UUID.randomUUID().toString(), newPassword, newPassword));
        error(response, 400);
        assertThat(json.readTree(response.getBody()).path("message").asText()).isEqualTo("Current password is incorrect");
        assertUnchanged();
    }

    @Test
    void mismatchedConfirmationDoesNotChangePassword() throws Exception {
        error(call(HttpMethod.PUT, ME + "/password", tokenA,
                passwordBody(oldPassword, newPassword, UUID.randomUUID().toString())), 400);
        assertUnchanged();
    }

    static Stream<String> invalidPasswords() {
        return Stream.of(null, "", "        ", "a".repeat(7), "a".repeat(73), "é".repeat(37));
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void invalidNewPasswordIsRejectedWithoutWriting(String password) throws Exception {
        error(call(HttpMethod.PUT, ME + "/password", tokenA, passwordBody(oldPassword, password, password)), 400);
        assertUnchanged();
    }

    static Stream<String> validBoundaryPasswords() {
        return Stream.of("a".repeat(8), "b".repeat(72), "é".repeat(36));
    }

    @ParameterizedTest
    @MethodSource("validBoundaryPasswords")
    void acceptsPasswordPolicyBoundaries(String password) throws Exception {
        data(call(HttpMethod.PUT, ME + "/password", tokenA, passwordBody(oldPassword, password, password)), 200);
        assertThat(passwordEncoder.matches(password, savedA().getPassword())).isTrue();
        data(login(userA.getEmail(), password), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"currentPassword", "newPassword", "confirmPassword"})
    void missingPasswordFieldsReturn400(String field) throws Exception {
        Map<String, Object> body = passwordBody(oldPassword, newPassword, newPassword);
        body.remove(field);
        error(call(HttpMethod.PUT, ME + "/password", tokenA, body), 400);
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "email", "role", "passwordHash"})
    void passwordRequestRejectsExtraFields(String field) throws Exception {
        Map<String, Object> body = passwordBody(oldPassword, newPassword, newPassword);
        body.put(field, "ADMIN");
        error(call(HttpMethod.PUT, ME + "/password", tokenA, body), 400);
        assertUnchanged();
    }

    @Test
    void passwordRequestsAndErrorsDoNotLogCredentials(CapturedOutput output) throws Exception {
        int start = output.getAll().length();
        error(changeWithoutClientLogging(passwordBody(oldPassword, newPassword, UUID.randomUUID().toString())), 400);
        error(changeWithoutClientLogging(passwordBody(oldPassword, "short", "short")), 400);
        data(changeWithoutClientLogging(passwordBody(oldPassword, newPassword, newPassword)), 200);
        String requestLogs = output.getAll().substring(start);
        assertThat(requestLogs).contains("ChangePasswordRequest[credentials redacted]");
        assertThat(requestLogs).doesNotContain(oldPassword, newPassword, userA.getPassword(), savedA().getPassword());
        assertThat(new ChangePasswordRequest(oldPassword, newPassword, newPassword).toString())
                .doesNotContain(oldPassword, newPassword);
    }

    @Test
    void adminCanUpdateOwnProfileWithoutChangingRole() throws Exception {
        userA.setRole(Role.ADMIN);
        users.saveAndFlush(userA);
        String adminToken = data(login(userA.getEmail(), oldPassword), 200).path("accessToken").asText();
        assertThat(data(call(HttpMethod.PUT, ME, adminToken, profile("Admin Updated", null)), 200)
                .path("role").asText()).isEqualTo("ADMIN");
        data(call(HttpMethod.PUT, ME + "/password", adminToken, passwordBody(oldPassword, newPassword, newPassword)), 200);
        assertThat(savedA().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void simultaneousChangesCannotReuseTheSameOldPassword() throws Exception {
        String secondPassword = UUID.randomUUID().toString();
        List<Integer> statuses = concurrent(
                () -> passwordChange(oldPassword, newPassword), () -> passwordChange(oldPassword, secondPassword));
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        String winningPassword = statuses.get(0) == 200 ? newPassword : secondPassword;
        assertThat(passwordEncoder.matches(winningPassword, savedA().getPassword())).isTrue();
    }

    @Test
    void simultaneousProfileAndPasswordChangesPreserveBothWrites() throws Exception {
        assertThat(concurrent(() -> {
            try {
                return call(HttpMethod.PUT, ME, tokenA, profile("Alice Concurrent", "0907654321"));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }, () -> passwordChange(oldPassword, newPassword))).containsExactly(200, 200);
        assertThat(savedA().getFullName()).isEqualTo("Alice Concurrent");
        assertThat(savedA().getPhone()).isEqualTo("0907654321");
        assertThat(passwordEncoder.matches(newPassword, savedA().getPassword())).isTrue();
    }

    private ResponseEntity<String> changeWithoutClientLogging(Map<String, Object> body) throws Exception {
        // Use the JDK client for log assertions: RestTemplate logs outgoing raw JSON at DEBUG.
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ME + "/password"))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + tokenA)
                    .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }
    }

    private ResponseEntity<String> passwordChange(String current, String desired) {
        try {
            return call(HttpMethod.PUT, ME + "/password", tokenA, passwordBody(current, desired, desired));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<Integer> concurrent(Supplier<ResponseEntity<String>> firstCall,
                                     Supplier<ResponseEntity<String>> secondCall) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
                users.findByEmailForUpdate(userA.getEmail()).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test lock timed out");
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

    private Map<String, Object> profile(String name, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", name);
        body.put("phone", phone);
        return body;
    }

    private Map<String, Object> passwordBody(String current, String desired, String confirmation) {
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", current);
        body.put("newPassword", desired);
        body.put("confirmPassword", confirmation);
        return body;
    }

    private ResponseEntity<String> login(String email, String password) throws Exception {
        return call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", password));
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method,
                new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response, int expected) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(expected);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body.path("data");
    }

    private void error(ResponseEntity<String> response, int expected) throws Exception {
        assertThat(response.getStatusCode().value()).as("Response: %s", response.getBody()).isEqualTo(expected);
        assertThat(json.readTree(response.getBody()).path("success").asBoolean()).isFalse();
        assertSafe(response.getBody());
    }

    private void assertSafe(String response) {
        assertThat(response).doesNotContain(oldPassword, newPassword, userA.getPassword(),
                "\"password\"", "\"passwordHash\"", "\"currentPassword\"", "\"newPassword\"", "\"authorities\"");
    }

    private void assertUnchanged() {
        UserEntity saved = savedA();
        assertThat(saved.getFullName()).isEqualTo(userA.getFullName());
        assertThat(saved.getPhone()).isEqualTo(userA.getPhone());
        assertThat(saved.getPassword()).isEqualTo(userA.getPassword());
        assertThat(saved.getRole()).isEqualTo(userA.getRole());
        assertThat(saved.getEmail()).isEqualTo(userA.getEmail());
    }

    private UserEntity savedA() {
        return users.findById(userA.getId()).orElseThrow();
    }

    private UserEntity user(String name, String email, String hash) {
        return UserEntity.builder().fullName(name).email(email).password(hash).phone("0901234567")
                .role(Role.USER).status(UserStatus.ACTIVE).build();
    }
}
