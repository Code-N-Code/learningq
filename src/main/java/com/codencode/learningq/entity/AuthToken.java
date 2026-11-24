package com.codencode.learningq.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_tokens")
public class AuthToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, updatable = false, length = 16)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, updatable = false, length = 16)
    private UUID userId;

    @Convert(converter = TokenPurposeConverter.class)
    @Column(nullable = false, updatable = false, length = 30)
    private TokenPurpose purpose;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 32)
    private byte[] tokenHash;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "used_at", columnDefinition = "DATETIME(6)")
    private Instant usedAt;

    protected AuthToken() {}

    public static AuthToken create(UUID userId, TokenPurpose purpose, byte[] hash,
                                   Instant createdAt, Instant expiresAt) {
        AuthToken token = new AuthToken();
        token.userId = Objects.requireNonNull(userId);
        token.purpose = Objects.requireNonNull(purpose);
        if (hash == null || hash.length != 32) {
            throw new IllegalArgumentException("Token hash must contain 32 bytes");
        }
        token.tokenHash = hash.clone();
        token.createdAt = Objects.requireNonNull(createdAt).truncatedTo(ChronoUnit.MICROS);
        token.expiresAt = Objects.requireNonNull(expiresAt).truncatedTo(ChronoUnit.MICROS);
        if (!token.expiresAt.isAfter(token.createdAt)) {
            throw new IllegalArgumentException("Token expiry must follow creation");
        }
        return token;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public TokenPurpose getPurpose() { return purpose; }
    public byte[] getTokenHash() { return tokenHash.clone(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
