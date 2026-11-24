package com.codencode.learningq.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.codencode.learningq.entity.*;
import com.codencode.learningq.repository.*;
import com.codencode.learningq.security.AuthTokenGenerator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the designated development/test MariaDB; removes only this test's user. */
@SpringBootTest
class AuthTokenPersistenceTests {
    @Autowired AuthTokenService service;
    @Autowired AuthTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired AuthTokenGenerator generator;
    @Autowired PlatformTransactionManager transactions;
    private UUID userId;

    @BeforeEach
    void createAccount() {
        String email = "token-test-" + UUID.randomUUID() + "@example.com";
        userId = users.saveAndFlush(User.create(email, email, "test-only-hash", null, Instant.now())).getId();
    }

    @AfterEach
    void cleanup() {
        if (userId != null) users.deleteById(userId);
    }

    @Test
    void roundTripsHashesPurposesAndExpiryAndReplacesOnlySamePurpose() {
        var first = service.issue(userId, TokenPurpose.EMAIL_VERIFICATION);
        var reset = service.issue(userId, TokenPurpose.PASSWORD_RESET);
        var replacement = service.issue(userId, TokenPurpose.EMAIL_VERIFICATION);
        AuthToken old = find(first);
        AuthToken current = find(replacement);
        assertEquals(userId, current.getUserId());
        assertEquals(TokenPurpose.EMAIL_VERIFICATION, current.getPurpose());
        assertArrayEquals(generator.hash(replacement.rawToken()), current.getTokenHash());
        assertEquals(replacement.expiresAt(), current.getExpiresAt());
        assertEquals(current.getCreatedAt(), old.getExpiresAt());
        assertNull(old.getUsedAt());
        assertTrue(find(reset).getExpiresAt().isAfter(current.getCreatedAt()));
        users.deleteById(userId);
        assertTrue(tokens.findById(current.getId()).isEmpty());
        userId = null;
    }

    @Test
    void outerRollbackRestoresPreviousTokenAndRemovesReplacement() {
        var first = service.issue(userId, TokenPurpose.EMAIL_VERIFICATION);
        var issued = new ArrayList<AuthTokenService.IssuedToken>();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            issued.add(service.issue(userId, TokenPurpose.EMAIL_VERIFICATION));
            status.setRollbackOnly();
        });
        assertEquals(first.expiresAt(), find(first).getExpiresAt());
        assertTrue(tokens.findByTokenHash(generator.hash(issued.getFirst().rawToken())).isEmpty());
    }

    @Test
    void concurrentIssuanceLeavesExactlyOneActiveToken() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<AuthTokenService.IssuedToken> issue = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return service.issue(userId, TokenPurpose.EMAIL_VERIFICATION);
            };
            var a = executor.submit(issue);
            var b = executor.submit(issue);
            start.countDown();
            // Wait for BOTH transactions to commit before loading either row.
            // Otherwise the first read can capture a still-active token just
            // before the other worker expires it, leaving a stale Java object.
            var firstIssued = a.get(30, TimeUnit.SECONDS);
            var secondIssued = b.get(30, TimeUnit.SECONDS);
            var first = find(firstIssued);
            var second = find(secondIssued);
            Instant latest = first.getCreatedAt().isAfter(second.getCreatedAt())
                    ? first.getCreatedAt() : second.getCreatedAt();
            assertEquals(1, java.util.stream.Stream.of(first, second)
                    .filter(t -> t.getExpiresAt().isAfter(latest)).count());
        }
    }

    private AuthToken find(AuthTokenService.IssuedToken issued) {
        return tokens.findByTokenHash(generator.hash(issued.rawToken())).orElseThrow();
    }
}
