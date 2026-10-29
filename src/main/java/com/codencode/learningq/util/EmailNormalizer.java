package com.codencode.learningq.util;

import java.util.Locale;

public class EmailNormalizer {
    private EmailNormalizer() {
        // Utility class: instances are unnecessary.
    }

    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }

        String trimmed = email.trim();

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }
}
