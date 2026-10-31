package com.codencode.learningq.security;

import java.util.Base64;

import com.codencode.learningq.config.PasswordConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig({PasswordConfig.class, PasswordService.class})
class PasswordServiceTests {

    @Autowired
    PasswordService passwords;

    @Test
    void argon2idHashUsesConfiguredParametersAndFitsTheDatabaseColumn() {
        String raw = "test-only long passphrase";
        String encoded = passwords.encodeNewPassword(raw);

        assertTrue(encoded.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertFalse(encoded.contains(raw));
        assertTrue(encoded.length() <= 255);
        String[] parts = encoded.split("\\$");
        assertEquals(16, Base64.getDecoder().decode(parts[4]).length);
        assertEquals(32, Base64.getDecoder().decode(parts[5]).length);
        assertTrue(passwords.matches(raw, encoded));
        assertFalse(passwords.matches("wrong test-only passphrase", encoded));
    }

    @Test
    void repeatedEncodingUsesDifferentRandomSalts() {
        String raw = "test-only long passphrase";
        String first = passwords.encodeNewPassword(raw);
        String second = passwords.encodeNewPassword(raw);

        assertNotEquals(first, second);
        assertNotEquals(first.split("\\$")[4], second.split("\\$")[4]);
        assertTrue(passwords.matches(raw, first));
        assertTrue(passwords.matches(raw, second));
    }

    @Test
    void surroundingSpacesAreSignificant() {
        String raw = " test-only passphrase ";
        String encoded = passwords.encodeNewPassword(raw);

        assertTrue(passwords.matches(raw, encoded));
        assertFalse(passwords.matches(raw.trim(), encoded));
    }

    @Test
    void unicodeMaximumLengthAndSuffixesBeyond72BytesArePreserved() {
        String raw = "🚀".repeat(128);
        String encoded = passwords.encodeNewPassword(raw);

        assertTrue(passwords.matches(raw, encoded));
        assertFalse(passwords.matches("🚀".repeat(127) + "a", encoded));
        assertFalse(passwords.matches("🚀".repeat(127), encoded));
    }

    @Test
    void invalidNewPasswordsAreRejectedBeforeEncoding() {
        for (String raw : new String[] {null, "", "too short", "a".repeat(129)}) {
            assertThrows(IllegalArgumentException.class, () -> passwords.encodeNewPassword(raw));
        }
    }

    @Test
    void verificationRejectsMissingAndOversizedInput() {
        String encoded = passwords.encodeNewPassword("test-only long passphrase");

        assertFalse(passwords.matches(null, encoded));
        assertFalse(passwords.matches("", encoded));
        assertFalse(passwords.matches("a".repeat(129), encoded));
        assertFalse(passwords.matches("test-only long passphrase", null));
        assertFalse(passwords.matches("test-only long passphrase", ""));
    }
}
