package com.codencode.learningq.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, updatable = false, length = 16)
    private UUID id;

    @Column(name = "email",  nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized",  nullable = false, length = 254)
    private String emailNormalized;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "email_verified_at", columnDefinition = "DATETIME(6)")
    private Instant emailVerifiedAt;

    @Column(name = "auth_version", nullable = false)
    private long authVersion = 0L;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)"
    )
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(
            name = "updated_at",
            nullable = false,
            columnDefinition = "DATETIME(6)"
    )
    private Instant updatedAt;


    protected User() {}

    /**
     * The caller supplies the consistently normalized email, an encoded password
     * hash, and the time obtained from its Clock. No raw password belongs here.
     */
    public static User create(String email, String emailNormalized, String passwordHash,
                              String displayName, Instant now) {
        User user = new User();
        user.email = requireText(email, "email");
        user.emailNormalized = requireText(emailNormalized, "emailNormalized");
        user.passwordHash = requireText(passwordHash, "passwordHash");
        user.displayName = normalizeDisplayName(displayName);
        user.createdAt = databaseTime(now);
        user.updatedAt = user.createdAt;
        return user;
    }

    public void changeDisplayName(String displayName, Instant now) {
        String normalized = normalizeDisplayName(displayName);
        Instant changedAt = databaseTime(now);
        if (!Objects.equals(this.displayName, normalized)) {
            this.displayName = normalized;
            this.updatedAt = changedAt;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || trimmed.codePointCount(0, trimmed.length()) > 80) {
            throw new IllegalArgumentException("displayName must contain 1 to 80 characters");
        }
        return trimmed;
    }

    private static Instant databaseTime(Instant value) {
        return Objects.requireNonNull(value, "now is required").truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public long getAuthVersion() {
        return authVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
