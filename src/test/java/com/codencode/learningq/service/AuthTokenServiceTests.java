package com.codencode.learningq.service;

import java.time.*;
import java.util.Optional;
import java.util.UUID;
import com.codencode.learningq.config.AuthTokenProperties;
import com.codencode.learningq.entity.*;
import com.codencode.learningq.repository.*;
import com.codencode.learningq.security.AuthTokenGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTests {
    @Mock UserRepository users;
    @Mock AuthTokenRepository tokens;
    private final AuthTokenGenerator generator = new AuthTokenGenerator();
    private final Instant now = Instant.parse("2026-11-25T00:00:00.123456Z");

    private AuthTokenService service() {
        return new AuthTokenService(users, tokens, generator,
                new AuthTokenProperties(Duration.ofHours(24), Duration.ofMinutes(30)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void locksBeforeReplacingAndPersistsOnlyHashWithPurposeSpecificExpiry() {
        UUID id = UUID.randomUUID();
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(
                User.create("a@example.com", "a@example.com", "encoded", null, now)));
        for (TokenPurpose purpose : TokenPurpose.values()) {
            var issued = service().issue(id, purpose);
            var captured = ArgumentCaptor.forClass(AuthToken.class);
            var order = inOrder(users, tokens);
            order.verify(users).findByIdForUpdate(id);
            order.verify(tokens).expireUnused(id, purpose, now);
            order.verify(tokens).saveAndFlush(captured.capture());
            AuthToken saved = captured.getValue();
            assertEquals(id, saved.getUserId());
            assertEquals(purpose, saved.getPurpose());
            assertArrayEquals(generator.hash(issued.rawToken()), saved.getTokenHash());
            assertEquals(now, saved.getCreatedAt());
            assertEquals(now.plus(purpose == TokenPurpose.EMAIL_VERIFICATION
                    ? Duration.ofHours(24) : Duration.ofMinutes(30)), saved.getExpiresAt());
            assertEquals(saved.getExpiresAt(), issued.expiresAt());
            assertNull(saved.getUsedAt());
            assertFalse(issued.toString().contains(issued.rawToken()));
            clearInvocations(users, tokens);
        }
    }

    @Test
    void unknownAccountNeverReplacesOrInsertsTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> service().issue(UUID.randomUUID(), TokenPurpose.PASSWORD_RESET));
        verifyNoInteractions(tokens);
    }

    @Test
    void rejectsInvalidLifetimes() {
        for (Duration invalid : new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(1)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuthTokenProperties(invalid, Duration.ofMinutes(30)));
            assertThrows(IllegalArgumentException.class,
                    () -> new AuthTokenProperties(Duration.ofHours(24), invalid));
        }
    }
}
