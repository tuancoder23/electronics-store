package com.electronics.store.repository;

import com.electronics.store.entity.*;
import com.electronics.store.mapper.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Focused verification of the cleanup's fetch plans; distinct relationships expose N+1 queries.
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Import({ProductMapper.class, OrderMapper.class, OrderItemMapper.class, PaymentMapper.class})
class BackendReadQueryTest {
    @Autowired EntityManager entityManager;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired CartItemRepository cartItems;
    @Autowired ProductMapper productMapper;
    @Autowired OrderMapper orderMapper;
    @Autowired JdbcTemplate jdbc;
    private Statistics statistics;
    private Long userId;
    private Long cartId;

    @BeforeEach
    void setUp() {
        UserEntity user = UserEntity.builder().fullName("Customer").email("query@example.test")
                .password("unused-test-password").build();
        entityManager.persist(user);
        userId = user.getId();
        CartEntity cart = CartEntity.builder().user(user).build();
        entityManager.persist(cart);
        cartId = cart.getId();
        for (int i = 0; i < 4; i++) {
            CategoryEntity category = CategoryEntity.builder().name("Category " + i).slug("category-" + i).build();
            BrandEntity brand = BrandEntity.builder().name("Brand " + i).slug("brand-" + i).build();
            entityManager.persist(category);
            entityManager.persist(brand);
            ProductEntity product = ProductEntity.builder().name("Product " + i).slug("product-" + i)
                    .price(BigDecimal.TEN).quantity(10).category(category).brand(brand).build();
            entityManager.persist(product);
            entityManager.persist(CartItemEntity.builder().cart(cart).product(product).quantity(1).build());
            OrderEntity order = OrderEntity.builder().user(user).receiverName("Customer").phone("0901234567")
                    .shippingAddress("Test street").subtotal(BigDecimal.TEN).totalAmount(BigDecimal.TEN)
                    .paymentMethod(PaymentMethod.COD).build();
            order.addItem(OrderItemEntity.builder().productId(product.getId()).productName(product.getName())
                    .quantity(1).unitPrice(BigDecimal.TEN).lineTotal(BigDecimal.TEN).build());
            entityManager.persist(order);
            if (i != 0) {
                PaymentEntity payment = PaymentEntity.builder().order(order).method(PaymentMethod.COD)
                        .amount(BigDecimal.TEN).build();
                entityManager.persist(payment);
                order.setPayment(payment);
            }
        }
        entityManager.flush();
        statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        clearReadState();
    }

    @Test
    void productPageFetchesCategoryAndBrandWithoutPerRowQueries() {
        var page = products.findAll((Specification<ProductEntity>) (root, query, builder) -> builder.conjunction(),
                PageRequest.of(0, 3)).map(productMapper::toResponse);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).hasSize(3).allSatisfy(product -> {
            assertThat(product.category().name()).startsWith("Category");
            assertThat(product.brand().name()).startsWith("Brand");
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2); // page + count
    }

    @Test
    void orderPagesFetchPaymentsAndBatchItemsWithoutInMemoryPagination() {
        var page = orders.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 3))
                .map(orderMapper::toResponse);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).hasSize(3).allSatisfy(order -> assertThat(order.items()).hasSize(1));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3); // page with payment + count + batched items
        clearReadState();
        var adminPage = orders.findAll((Specification<OrderEntity>) (root, query, builder) -> builder.conjunction(),
                PageRequest.of(0, 3)).map(orderMapper::toResponse);
        assertThat(adminPage.getTotalElements()).isEqualTo(4);
        assertThat(adminPage.getContent()).hasSize(3).allSatisfy(order -> assertThat(order.items()).hasSize(1));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void cartDisplayFetchesProductsButCheckoutKeepsProductsUninitialized() {
        var displayItems = cartItems.findForDisplayByCartId(cartId);
        assertThat(displayItems).hasSize(4).allSatisfy(item ->
                assertThat(item.getProduct().getName()).startsWith("Product"));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        clearReadState();
        var checkoutItems = cartItems.findByCartIdOrderByIdAsc(cartId);
        assertThat(checkoutItems).hasSize(4).allSatisfy(item ->
                assertThat(org.hibernate.Hibernate.isInitialized(item.getProduct())).isFalse());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private void clearReadState() {
        entityManager.clear();
        statistics.clear();
    }

    @Test
    void negativeStockIsRejectedByDatabaseAndJpa() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update products set quantity=-1"));
        ProductEntity product = products.findAll().getFirst();
        product.setQuantity(-1);
        org.junit.jupiter.api.Assertions.assertThrows(jakarta.validation.ConstraintViolationException.class,
                () -> entityManager.flush());
    }
}
