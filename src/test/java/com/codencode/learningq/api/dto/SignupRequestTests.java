package com.codencode.learningq.api.dto;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupRequestTests {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void trimsEmailAndDisplayNameButPreservesPasswordExactly() {
        String password = " private password ";
        SignupRequest request = new SignupRequest(" Learner@Example.com ", password, " Learner ");
        assertTrue(validator.validate(request).isEmpty());
        assertEquals("Learner@Example.com", request.email());
        assertEquals("Learner", request.displayName());
        assertEquals(password, request.password());
        assertFalse(request.toString().contains(password));
        assertFalse(request.toString().contains("Learner@Example.com"));
    }

    @Test
    void optionalDisplayNameCanBeMissing() {
        SignupRequest request = new SignupRequest("learner@example.com", "test-password", null);
        assertTrue(validator.validate(request).isEmpty());
        assertNull(request.displayName());
    }

    @Test
    void rejectsMissingMalformedNonAsciiAndOversizedEmail() {
        for (String email : new String[] {null, " ", "not-an-email", "léarner@example.com", "a\r\nb@example.com",
                "a".repeat(243) + "@example.com"}) {
            assertTrue(invalidFields(new SignupRequest(email, "test-password", null)).contains("email"));
        }
    }

    @Test
    void passwordPolicyCountsUnicodeAndRejectsMalformedSurrogates() {
        for (String password : new String[] {null, "", "a".repeat(11), "a".repeat(129),
                "a".repeat(12) + '\uD800'}) {
            assertTrue(invalidFields(new SignupRequest("learner@example.com", password, null)).contains("password"));
        }
        for (String password : new String[] {"a".repeat(12), "🚀".repeat(128)}) {
            assertTrue(validator.validate(new SignupRequest("learner@example.com", password, null)).isEmpty());
        }
    }

    @Test
    void displayNameUsesCodePointLimitAndRejectsBlankOrInvalidUnicode() {
        assertTrue(validator.validate(new SignupRequest("learner@example.com", "test-password",
                "🚀".repeat(80))).isEmpty());
        for (String name : new String[] {" ", "\t", "🚀".repeat(81), "name" + '\uD800'}) {
            assertTrue(invalidFields(new SignupRequest("learner@example.com", "test-password", name))
                    .contains("displayName"));
        }
    }

    private static Set<String> invalidFields(SignupRequest request) {
        return validator.validate(request).stream().map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
