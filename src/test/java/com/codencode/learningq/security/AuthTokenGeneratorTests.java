package com.codencode.learningq.security;

import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthTokenGeneratorTests {
    private final AuthTokenGenerator generator = new AuthTokenGenerator();

    @Test
    void generatesIndependentUrlSafe256BitTokens() {
        String first = generator.generate();
        String second = generator.generate();
        assertNotEquals(first, second);
        assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
    }

    @Test
    void hashesUsingTheKnownSha256VectorAndFreshArrays() {
        byte[] hash = generator.hash("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HexFormat.of().formatHex(hash));
        hash[0] = 0;
        assertNotEquals(hash[0], generator.hash("abc")[0]);
    }
}
