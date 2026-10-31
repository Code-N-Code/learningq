package com.codencode.learningq.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (!canVerify(password) || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must contain " + MIN_LENGTH + " to "
                    + MAX_LENGTH + " Unicode characters.");
        }
    }

    static boolean canVerify(String password) {
        // Bound work before scanning input. A code point takes at most two UTF-16 units.
        // Reject malformed surrogate sequences rather than allowing UTF-8 replacement.
        return password != null && !password.isEmpty() && password.length() <= MAX_LENGTH * 2
                && password.codePointCount(0, password.length()) <= MAX_LENGTH
                && StandardCharsets.UTF_8.newEncoder().canEncode(password);
    }
}
