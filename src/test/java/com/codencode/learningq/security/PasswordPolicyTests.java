package com.codencode.learningq.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTests {

    @Test
    void lengthBoundariesAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("a".repeat(11)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("a".repeat(12)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("a".repeat(128)));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("a".repeat(129)));
    }

    @Test
    void unicodeCodePointsAreCountedInsteadOfUtf16Units() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("🚀".repeat(11)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("🚀".repeat(12)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("🚀".repeat(128)));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("🚀".repeat(129)));
    }

    @Test
    void noCompositionRulesOrWhitespaceTrimmingAreApplied() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("lowercase letters only"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("123456789012"));
        assertDoesNotThrow(() -> PasswordPolicy.validate(" " + "a".repeat(10) + " "));
        assertDoesNotThrow(() -> PasswordPolicy.validate(" ".repeat(12)));
    }

    @Test
    void nullEmptyAndInvalidUnicodeAreRejectedWithoutEchoingTheValue() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(""));
        String invalid = "private-password-marker" + '\uD800';
        var error = assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(invalid));
        assertFalse(error.getMessage().contains("private-password-marker"));
        assertFalse(PasswordPolicy.canVerify(invalid));
    }
}
