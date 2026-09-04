package com.electronics.store.cart;

import com.electronics.store.entity.*;
import com.electronics.store.repository.*;
import com.electronics.store.security.CustomUserDetails;
import com.electronics.store.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CartIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired BrandRepository brandRepository;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItemRepository;

    private UserEntity userA;
    private UserEntity userB;
    private ProductEntity productA;
    private ProductEntity productB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(user("User A", "user-a@example.com"));
        userB = userRepository.save(user("User B", "user-b@example.com"));
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .name("Phones").slug("phones").build());
        BrandEntity brand = brandRepository.save(BrandEntity.builder()
                .name("Acme").slug("acme").build());
        productA = productRepository.save(product("Product A", "product-a", 10,
                new BigDecimal("100.00"), new BigDecimal("80.00"), category, brand));
        productB = productRepository.save(product("Product B", "product-b", 3,
                new BigDecimal("50.00"), null, category, brand));
        tokenA = jwtService.generateToken(new CustomUserDetails(userA));
        tokenB = jwtService.generateToken(new CustomUserDetails(userB));
    }

    @Test
    void emptyCartIsCreatedLazilyAndRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/cart").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0));

        assertThat(cartRepository.findByUserId(userA.getId())).isPresent();
        assertThat(cartRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateProductIncrementsQuantityAndCalculatesTotals() throws Exception {
        add(tokenA, productA.getId(), 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].effectivePrice").value(80.0))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(160.0));

        add(tokenA, productA.getId(), 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.subtotal").value(240.0));

        Long cartId = cartRepository.findByUserId(userA.getId()).orElseThrow().getId();
        assertThat(cartItemRepository.findByCartIdOrderByIdAsc(cartId)).hasSize(1);
        assertThat(productRepository.findById(productA.getId()).orElseThrow().getQuantity()).isEqualTo(10);
    }

    @Test
    void stockStatusAndRequestValidationAreEnforced() throws Exception {
        add(tokenA, productA.getId(), 3).andExpect(status().isCreated());
        add(tokenA, productA.getId(), 8).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));
        add(tokenA, productB.getId(), 4).andExpect(status().isBadRequest());
        add(tokenA, productB.getId(), 0).andExpect(status().isBadRequest());

        productB.setStatus(ProductStatus.INACTIVE);
        productRepository.save(productB);
        add(tokenA, productB.getId(), 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("inactive")));
    }

    @Test
    void updateRemoveAndClearReturnFreshCart() throws Exception {
        add(tokenA, productA.getId(), 2).andExpect(status().isCreated());
        add(tokenA, productB.getId(), 1).andExpect(status().isCreated());
        Long cartId = cartRepository.findByUserId(userA.getId()).orElseThrow().getId();
        Long itemAId = cartItemRepository.findByCartIdAndProductId(cartId, productA.getId()).orElseThrow().getId();
        Long itemBId = cartItemRepository.findByCartIdAndProductId(cartId, productB.getId()).orElseThrow().getId();

        mockMvc.perform(put("/api/cart/items/{id}", itemAId).header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(6))
                .andExpect(jsonPath("$.data.subtotal").value(450.0));

        mockMvc.perform(delete("/api/cart/items/{id}", itemBId).header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(delete("/api/cart").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0));
        assertThat(cartRepository.findByUserId(userA.getId())).isPresent();
    }

    @Test
    void anotherUserCannotUpdateOrDeleteCartItem() throws Exception {
        add(tokenA, productA.getId(), 1).andExpect(status().isCreated());
        Long cartId = cartRepository.findByUserId(userA.getId()).orElseThrow().getId();
        Long itemId = cartItemRepository.findByCartIdOrderByIdAsc(cartId).getFirst().getId();

        mockMvc.perform(put("/api/cart/items/{id}", itemId).header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/cart/items/{id}", itemId).header("Authorization", bearer(tokenB)))
                .andExpect(status().isForbidden());

        assertThat(cartItemRepository.findById(itemId)).isPresent();
        assertThat(cartItemRepository.findById(itemId).orElseThrow().getQuantity()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions add(String token, Long productId, int quantity)
            throws Exception {
        return mockMvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private UserEntity user(String name, String email) {
        return UserEntity.builder().fullName(name).email(email).password(passwordEncoder.encode("Password123"))
                .role(Role.USER).status(UserStatus.ACTIVE).build();
    }

    private ProductEntity product(String name, String slug, int stock, BigDecimal price, BigDecimal discount,
                                  CategoryEntity category, BrandEntity brand) {
        return ProductEntity.builder().name(name).slug(slug).quantity(stock).price(price).discountPrice(discount)
                .status(ProductStatus.ACTIVE).category(category).brand(brand).build();
    }
}
