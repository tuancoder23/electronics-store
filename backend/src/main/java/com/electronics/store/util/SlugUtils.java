package com.electronics.store.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility class for URL-friendly slug generation.
 */
public final class SlugUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_TRAILING_HYPHENS = Pattern.compile("^-+|-+$");

    private SlugUtils() {
    }

    /**
     * Converts a string into a clean, URL-safe slug.
     * Handles Vietnamese characters and accents properly.
     *
     * @param input the input string
     * @return URL-friendly slug string
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "category";
        }

        // Replace Vietnamese 'đ' / 'Đ' characters before NFD normalization
        String text = input.replace("đ", "d").replace("Đ", "d");

        // Decompose diacritics and strip them
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");

        // Convert to lowercase
        String lower = withoutDiacritics.toLowerCase(Locale.ROOT);

        // Replace non-alphanumeric characters with hyphens
        String slug = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");

        // Remove leading and trailing hyphens
        slug = LEADING_TRAILING_HYPHENS.matcher(slug).replaceAll("");

        return slug.isEmpty() ? "category" : slug;
    }
}
