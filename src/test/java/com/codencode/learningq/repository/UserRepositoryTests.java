package com.codencode.learningq.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import com.codencode.learningq.entity.User;
import com.codencode.learningq.util.EmailNormalizer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses DB_URL, DB_USERNAME and DB_PASSWORD for the designated development/test
 * MariaDB. Each test rolls back its rows; Flyway schema changes persist.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class UserRepositoryTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-30T00:00:00.123456Z");
    private static final String TEST_HASH = "test-only-encoded-hash-placeholder";

    @Autowired
    UserRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void uuidAndUtcMicrosecondTimestampsRoundTripThroughTheDatabase() {
        User user = newUser(uniqueEmail(), "Learner 🚀");
        repository.saveAndFlush(user);
        UUID id = user.getId();
        assertNotNull(id);
        entityManager.clear();

        User reloaded = repository.findById(id).orElseThrow();

        assertEquals(id, reloaded.getId());
        assertEquals(user.getEmail(), reloaded.getEmail());
        assertEquals(user.getEmailNormalized(), reloaded.getEmailNormalized());
        assertEquals(TEST_HASH, reloaded.getPasswordHash());
        assertEquals("Learner 🚀", reloaded.getDisplayName());
        assertNull(reloaded.getEmailVerifiedAt());
        assertEquals(0L, reloaded.getAuthVersion());
        assertEquals(CREATED_AT, reloaded.getCreatedAt());
        assertEquals(CREATED_AT, reloaded.getUpdatedAt());

        // Inspect physical storage too: a cached or symmetric Java conversion
        // must not conceal an incorrect UUID encoding or a shifted UTC value.
        assertEquals(16, jdbc.queryForObject(
                "SELECT OCTET_LENGTH(id) FROM users WHERE id = ?", Integer.class, uuidBytes(id)));
        assertEquals(id.toString().replace("-", "").toUpperCase(Locale.ROOT),
                jdbc.queryForObject("SELECT HEX(id) FROM users WHERE id = ?", String.class, uuidBytes(id)));
        LocalDateTime storedTime = jdbc.queryForObject(
                "SELECT created_at FROM users WHERE id = ?",
                (result, row) -> result.getObject(1, LocalDateTime.class), uuidBytes(id));
        assertEquals(LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC), storedTime);
    }

    @Test
    void lookupUsesTheSameNormalizedIdentifierForDifferentInputFormatting() {
        String email = uniqueEmail();
        User user = repository.saveAndFlush(newUser(email, "Learner"));
        entityManager.clear();
        String normalized = EmailNormalizer.normalize("  " + email.toUpperCase(Locale.ROOT) + "  ");

        assertEquals(user.getId(), repository.findByEmailNormalized(normalized).orElseThrow().getId());
        assertTrue(repository.existsByEmailNormalized(normalized));

        String absent = uniqueEmail();
        assertTrue(repository.findByEmailNormalized(absent).isEmpty());
        assertFalse(repository.existsByEmailNormalized(absent));
    }

    @Test
    void databaseRejectsDuplicateNormalizedEmails() {
        String email = uniqueEmail();
        repository.saveAndFlush(newUser(email, "First"));
        entityManager.clear();
        User duplicate = newUser("  " + email.toUpperCase(Locale.ROOT) + "  ", "Second");

        // Flush forces MariaDB to enforce the unique constraint now.
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }

    @Test
    void displayNameUpdatePersistsWithoutChangingCreationTime() {
        User original = repository.saveAndFlush(newUser(uniqueEmail(), "Before"));
        UUID id = original.getId();
        entityManager.clear();
        User managed = repository.findById(id).orElseThrow();
        Instant changedAt = CREATED_AT.plusSeconds(3600);

        managed.changeDisplayName("  After  ", changedAt);
        repository.flush();
        entityManager.clear();
        User reloaded = repository.findById(id).orElseThrow();

        assertEquals("After", reloaded.getDisplayName());
        assertEquals(CREATED_AT, reloaded.getCreatedAt());
        assertEquals(changedAt, reloaded.getUpdatedAt());
    }

    @Test
    void optionalProfileValuesCanBeNull() {
        User user = repository.saveAndFlush(newUser(uniqueEmail(), null));
        entityManager.clear();

        User reloaded = repository.findById(user.getId()).orElseThrow();

        assertNull(reloaded.getDisplayName());
        assertNull(reloaded.getEmailVerifiedAt());
    }

    @Test
    void dotsAndPlusSuffixesIdentifyDistinctAccounts() {
        String prefix = "repo-" + UUID.randomUUID();
        User dotted = repository.saveAndFlush(newUser(prefix + ".learner@example.com", null));
        User plain = repository.saveAndFlush(newUser(prefix + "learner@example.com", null));
        User plus = repository.saveAndFlush(newUser(prefix + "learner+practice@example.com", null));
        entityManager.clear();

        assertNotEquals(dotted.getId(), plain.getId());
        assertNotEquals(plain.getId(), plus.getId());
        for (User user : new User[] {dotted, plain, plus}) {
            assertEquals(user.getId(), repository.findByEmailNormalized(user.getEmailNormalized())
                    .orElseThrow().getId());
        }
    }

    private static User newUser(String email, String displayName) {
        return User.create(email.trim(), EmailNormalizer.normalize(email), TEST_HASH, displayName, CREATED_AT);
    }

    private static String uniqueEmail() {
        return "repo-" + UUID.randomUUID() + "@example.com";
    }

    private static byte[] uuidBytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
