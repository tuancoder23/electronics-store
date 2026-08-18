package com.electronics.store.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugUtilsTest {

    @Test
    void shouldGenerateSlugFromSimpleEnglish() {
        assertEquals("laptop-gaming", SlugUtils.toSlug("Laptop Gaming"));
    }

    @Test
    void shouldGenerateSlugFromVietnameseWithAccents() {
        assertEquals("may-tinh-xach-tay", SlugUtils.toSlug("Máy tính xách tay"));
        assertEquals("dien-thoai-di-dong", SlugUtils.toSlug("Điện thoại di động"));
    }

    @Test
    void shouldHandleSpecialCharactersAndWhitespace() {
        assertEquals("laptop-pro-15-inch-more", SlugUtils.toSlug("  Laptop Pro (15-inch) & More!  "));
    }

    @Test
    void shouldHandleNullOrBlank() {
        assertEquals("category", SlugUtils.toSlug(null));
        assertEquals("category", SlugUtils.toSlug("   "));
    }
}
