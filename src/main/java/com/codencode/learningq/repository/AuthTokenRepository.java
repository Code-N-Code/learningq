package com.codencode.learningq.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.codencode.learningq.entity.AuthToken;
import com.codencode.learningq.entity.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByTokenHash(byte[] tokenHash);

    // Discover the lock key without putting a stale token entity into the
    // persistence context. This lookup alone never authorizes consumption.
    @Query("select t.userId from AuthToken t where t.tokenHash = :hash")
    Optional<UUID> findUserIdByTokenHash(@Param("hash") byte[] hash);

    // A conditional UPDATE is a current database read, including under MariaDB's
    // REPEATABLE READ isolation. Do not rely on an earlier snapshot lookup.
    // Keep the caller's managed user attached for the subsequent account change.
    @Modifying(flushAutomatically = true)
    @Query("""
            update AuthToken t set t.usedAt = :now
            where t.userId = :userId and t.tokenHash = :hash and t.purpose = :purpose
              and t.usedAt is null and t.expiresAt > :now
            """)
    int consumeIfValid(@Param("userId") UUID userId, @Param("hash") byte[] hash,
                       @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);

    // Caller must hold the user's write lock in the same transaction. Expire
    // replaced tokens without claiming that they were actually consumed.
    @Modifying(flushAutomatically = true)
    @Query("""
            update AuthToken t set t.expiresAt = :now
            where t.userId = :userId and t.purpose = :purpose
              and t.usedAt is null and t.expiresAt > :now
            """)
    int expireUnused(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose,
                     @Param("now") Instant now);
}
