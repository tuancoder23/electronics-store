package com.electronics.store.util;

import com.electronics.store.entity.ProductEntity;

import java.math.BigDecimal;

/** The price rule shared by cart display and checkout snapshots. */
public final class ProductPricing {
    private ProductPricing() { }

    public static BigDecimal effectivePrice(ProductEntity product) {
        BigDecimal price = product.getPrice();
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Product price is invalid: " + product.getName());
        }
        BigDecimal discount = product.getDiscountPrice();
        return discount != null && discount.signum() >= 0 && discount.compareTo(price) < 0 ? discount : price;
    }
}
