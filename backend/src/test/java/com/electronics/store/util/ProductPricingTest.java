package com.electronics.store.util;

import com.electronics.store.entity.ProductEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ProductPricingTest {
    @ParameterizedTest
    @CsvSource({"100.10,80.05,80.05", "100.10,0,0", "100.10,,100.10",
            "100.10,-0.01,100.10", "100.10,100.10,100.10", "100.10,101,100.10", "0,,0"})
    void sharedPriceRuleIsExactAndDoesNotMutateCatalog(String base, String discount, String expected) {
        ProductEntity product = ProductEntity.builder().price(new BigDecimal(base))
                .discountPrice(discount == null ? null : new BigDecimal(discount)).build();
        assertThat(ProductPricing.effectivePrice(product)).isEqualByComparingTo(expected);
        assertThat(product.getPrice()).isEqualByComparingTo(base);
        assertThat(product.getDiscountPrice()).isEqualTo(discount == null ? null : new BigDecimal(discount));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-0.01", "-100"})
    void discountCannotHideInvalidBasePrice(String price) {
        ProductEntity product = ProductEntity.builder().name("Invalid price")
                .price(price == null ? null : new BigDecimal(price)).discountPrice(BigDecimal.ZERO).build();
        assertThatThrownBy(() -> ProductPricing.effectivePrice(product))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Product price is invalid");
    }
}
