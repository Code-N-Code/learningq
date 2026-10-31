package com.codencode.learningq.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.codencode.learningq.api.dto.SignupRequest;
import com.codencode.learningq.api.dto.SignupResponse;
import com.codencode.learningq.repository.UserRepository;
import com.codencode.learningq.security.PasswordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real commits/rollbacks against DB_URL; cleanup targets only this test's random emails. */
@SpringBootTest
@Import(RegistrationPersistenceTests.FixedTime.class)
class RegistrationPersistenceTests {
    private static final Instant NOW = Instant.parse("2026-10-31T01:02:03.123456Z");
    private static final String PASSWORD = "test-only passphrase";
    private final List<String> testEmails = new ArrayList<>();

    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @AfterEach
    void removeOnlyTestAccounts() {
        for (String email : testEmails) {
            users.findByEmailNormalized(email).ifPresent(user -> users.deleteById(user.getId()));
        }
    }

    @Test
    void validSignupCommitsAnUnverifiedAccountWithHashAndFixedTimestamps() {
        String email = email();
        assertEquals(SignupResponse.processed(), registration.register(
                new SignupRequest(" " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD, " Learner ")));

        var user = users.findByEmailNormalized(email).orElseThrow();
        assertNotNull(user.getId());
        assertEquals(email.toUpperCase(Locale.ROOT), user.getEmail());
        assertEquals("Learner", user.getDisplayName());
        assertNull(user.getEmailVerifiedAt());
        assertEquals(0L, user.getAuthVersion());
        assertEquals(NOW, user.getCreatedAt());
        assertEquals(NOW, user.getUpdatedAt());
        assertTrue(passwords.matches(PASSWORD, user.getPasswordHash()));
        assertFalse(PASSWORD.equals(user.getPasswordHash()));
    }

    @Test
    void duplicateRollbackDoesNotModifyExistingAccountOrPoisonNextSignup() {
        String email = email();
        SignupResponse first = registration.register(new SignupRequest(email, PASSWORD, "Original"));
        var original = users.findByEmailNormalized(email).orElseThrow();

        SignupResponse duplicate = registration.register(new SignupRequest(
                email.toUpperCase(Locale.ROOT), "different test passphrase", "Replacement"));

        assertEquals(first, duplicate);
        var unchanged = users.findByEmailNormalized(email).orElseThrow();
        assertEquals(original.getId(), unchanged.getId());
        assertEquals(original.getPasswordHash(), unchanged.getPasswordHash());
        assertEquals("Original", unchanged.getDisplayName());
        assertEquals(original.getUpdatedAt(), unchanged.getUpdatedAt());
        assertEquals(SignupResponse.processed(), registration.register(new SignupRequest(email(), PASSWORD, null)));
    }

    @Test
    void concurrentCaseVariantSignupsCreateExactlyOneAccount() throws Exception {
        String email = email();
        CountDownLatch start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> {
                start.await();
                return registration.register(new SignupRequest(email, PASSWORD, "First"));
            });
            var second = workers.submit(() -> {
                start.await();
                return registration.register(new SignupRequest(email.toUpperCase(Locale.ROOT), PASSWORD, "Second"));
            });
            start.countDown();
            assertEquals(SignupResponse.processed(), first.get(30, TimeUnit.SECONDS));
            assertEquals(SignupResponse.processed(), second.get(30, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email_normalized = ?", Long.class, email));
        assertTrue(passwords.matches(PASSWORD, users.findByEmailNormalized(email).orElseThrow().getPasswordHash()));
    }

    @Test
    void coordinatorRefusesAnAmbientTransaction() {
        SignupRequest request = new SignupRequest(email(), PASSWORD, null);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThrows(IllegalTransactionStateException.class, () -> registration.register(request)));
        assertFalse(users.existsByEmailNormalized(request.email()));
    }

    private String email() {
        String email = "registration-test-" + UUID.randomUUID() + "@example.com";
        testEmails.add(email);
        return email;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
