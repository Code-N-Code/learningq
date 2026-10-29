package com.codencode.learningq.entity;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-30T00:00:00.123456789Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-30T01:00:00Z");

    @Test
    void creationUsesOneTimestampAtDatabasePrecision() {
        User user = newUser();

        assertEquals(Instant.parse("2026-10-30T00:00:00.123456Z"), user.getCreatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
        assertEquals("Learner", user.getDisplayName());
        assertNull(user.getEmailVerifiedAt());
        assertEquals(0L, user.getAuthVersion());
    }

    @Test
    void displayNameChangesPreserveCreationTimeAndAllowClearing() {
        User user = newUser();
        Instant originalCreatedAt = user.getCreatedAt();

        user.changeDisplayName("  New name  ", CHANGED_AT);

        assertEquals("New name", user.getDisplayName());
        assertEquals(CHANGED_AT, user.getUpdatedAt());
        assertEquals(originalCreatedAt, user.getCreatedAt());

        user.changeDisplayName(null, CHANGED_AT.plusSeconds(1));

        assertNull(user.getDisplayName());
        assertEquals(CHANGED_AT.plusSeconds(1), user.getUpdatedAt());
    }

    @Test
    void unchangedOrRejectedUpdatesPreserveExistingState() {
        User user = newUser();
        Instant originalUpdatedAt = user.getUpdatedAt();

        user.changeDisplayName(" Learner ", CHANGED_AT);
        assertThrows(IllegalArgumentException.class,
                () -> user.changeDisplayName("   ", CHANGED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> user.changeDisplayName("a".repeat(81), CHANGED_AT));
        assertThrows(NullPointerException.class,
                () -> user.changeDisplayName("Changed", null));

        assertEquals("Learner", user.getDisplayName());
        assertEquals(originalUpdatedAt, user.getUpdatedAt());
    }

    private static User newUser() {
        return User.create("Learner@example.com", "learner@example.com",
                "test-only-encoded-hash-placeholder", " Learner ", CREATED_AT);
    }
}
