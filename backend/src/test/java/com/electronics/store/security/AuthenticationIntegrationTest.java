package com.electronics.store.security;

import com.electronics.store.entity.Role;
import com.electronics.store.entity.UserEntity;
import com.electronics.store.entity.UserStatus;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Value("${app.jwt.secret}") String testJwtSecret;

    @Autowired UserRepository userRepository;
    @MockitoBean ProductService productService;
    @MockitoBean CategoryService categoryService;
    @MockitoBean BrandService brandService;
    @MockitoBean ProductImageService productImageService;
    @MockitoBean ProductSpecificationService productSpecificationService;

    private UserEntity user;
    private UserEntity admin;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        user = userRepository.save(createUser("User", "user@example.com", Role.USER));
        admin = userRepository.save(createUser("Admin", "admin@example.com", Role.ADMIN));
    }

    @Test
    void registerCreatesSafeDefaultUserWithHashedPassword() throws Exception {
        String body = """
                {"fullName":"Nguyen Van A","email":"new@example.com","password":"Password123","phone":"0987654321"}
                """;
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UserEntity saved = userRepository.findByEmail("new@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getRole()).isEqualTo(Role.USER);
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        org.assertj.core.api.Assertions.assertThat(saved.getPassword()).isNotEqualTo("Password123");
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("Password123", saved.getPassword())).isTrue();
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain("Password123");
    }

    @Test
    void duplicateRegisterReturnsConflictAndDoesNotSave() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"User\",\"email\":\"user@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        org.assertj.core.api.Assertions.assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void loginReturnsBearerTokenAndWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"WrongPassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void currentUserRequiresValidJwt() throws Exception {
        String token = jwtService.generateToken(new CustomUserDetails(user));
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userIsForbiddenFromAdminAndAdminPassesAuthorization() throws Exception {
        String userToken = jwtService.generateToken(new CustomUserDetails(user));
        String adminToken = jwtService.generateToken(new CustomUserDetails(admin));
        mockMvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void catalogueAndHealthGetEndpointsRemainPublic() throws Exception {
        String[] paths = {"/api/health", "/api/categories", "/api/brands", "/api/products",
                "/api/products/1", "/api/products/1/specifications", "/api/products/1/images",
                "/api/product-specifications/1", "/api/product-images/1"};
        for (String path : paths) {
            mockMvc.perform(get(path)).andExpect(status().is(not(401))).andExpect(status().is(not(403)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "no-subject", "no-expiration", "expired"})
    void incompleteOrExpiredJwtReturns401InsteadOfServerError(String mode) throws Exception {
        var builder = io.jsonwebtoken.Jwts.builder();
        if (!mode.equals("no-subject")) builder.subject(user.getEmail());
        if (!mode.equals("no-expiration")) {
            builder.expiration(Date.from(Instant.now().plusSeconds(mode.equals("expired") ? -60 : 60)));
        }
        String token = mode.equals("empty") ? "" : builder.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                testJwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
        for (String path : new String[]{"/api/users/me", "/api/products"}) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    void registrationRejectsPasswordsOverBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"User\",\"email\":\"new@example.com\",\"password\":\"" + "é".repeat(37) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must not exceed 72 UTF-8 bytes"));
        org.assertj.core.api.Assertions.assertThat(userRepository.existsByEmail("new@example.com")).isFalse();
    }

    @Test
    void configuredCorsPreflightWorksWithoutBypassingAuthentication() throws Exception {
        mockMvc.perform(options("/api/cart/items").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(post("/api/cart/items").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(options("/api/cart/items").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticationDtosRedactCredentialsWithoutChangingJsonContract() {
        String password = "sensitive-test-value";
        String token = "sensitive-test-token";
        org.assertj.core.api.Assertions.assertThat(new com.electronics.store.dto.request.LoginRequest(
                "user@example.com", password).toString()).doesNotContain(password);
        org.assertj.core.api.Assertions.assertThat(new com.electronics.store.dto.request.RegisterRequest(
                "User", "user@example.com", password, null).toString()).doesNotContain(password);
        org.assertj.core.api.Assertions.assertThat(new com.electronics.store.dto.response.AuthResponse(
                token, "Bearer", null).toString()).doesNotContain(token);
    }

    private UserEntity createUser(String name, String email, Role role) {
        return UserEntity.builder().fullName(name).email(email)
                .password(passwordEncoder.encode("Password123")).role(role).status(UserStatus.ACTIVE)
                .build();
    }
}
