package com.codencode.learningq.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import com.codencode.learningq.config.AuthTokenProperties;
import com.codencode.learningq.entity.AuthToken;
import com.codencode.learningq.entity.TokenPurpose;
import com.codencode.learningq.repository.AuthTokenRepository;
import com.codencode.learningq.repository.UserRepository;
import com.codencode.learningq.security.AuthTokenGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@EnableConfigurationProperties(AuthTokenProperties.class)
public class AuthTokenService {
    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final AuthTokenGenerator generator;
    private final AuthTokenProperties properties;
    private final Clock clock;

    public AuthTokenService(UserRepository users, AuthTokenRepository tokens, AuthTokenGenerator generator,
                            AuthTokenProperties properties, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.generator = generator;
        this.properties = properties;
        this.clock = clock;
    }

    // Joins the caller's transaction so later signup/mail integration can persist
    // the account and token together. Deliver the raw token only after commit.
    @Transactional
    public IssuedToken issue(UUID userId, TokenPurpose purpose) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(purpose);
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Token account does not exist"));
        // Read time AFTER acquiring the lock, including time spent waiting.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = now.plus(properties.ttlFor(purpose)).truncatedTo(ChronoUnit.MICROS);
        tokens.expireUnused(userId, purpose, now);
        String raw = generator.generate();
        tokens.saveAndFlush(AuthToken.create(userId, purpose, generator.hash(raw), now, expiresAt));
        return new IssuedToken(raw, expiresAt);
    }

    /**
     * Must be called from the transaction that changes the account. A failed
     * verification/reset then rolls back token consumption as well. The returned
     * UUID identifies the locked account; no authentication is established here.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID consume(String rawToken, TokenPurpose purpose) {
        Objects.requireNonNull(purpose);
        // Generated tokens are exactly 43 URL-safe characters. Bound untrusted
        // input before hashing/querying; never trim or otherwise rewrite it.
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new InvalidAuthTokenException();
        }
        byte[] hash = generator.hash(rawToken);
        UUID userId = tokens.findUserIdByTokenHash(hash).orElseThrow(InvalidAuthTokenException::new);
        users.findByIdForUpdate(userId).orElseThrow(InvalidAuthTokenException::new);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (tokens.consumeIfValid(userId, hash, purpose, now) != 1) {
            throw new InvalidAuthTokenException();
        }
        return userId;
    }

    public record IssuedToken(String rawToken, Instant expiresAt) {
        @Override
        public String toString() { return "IssuedToken[redacted]"; }
    }
}
