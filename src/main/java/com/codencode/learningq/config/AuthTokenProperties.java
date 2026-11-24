package com.codencode.learningq.config;

import java.time.Duration;
import com.codencode.learningq.entity.TokenPurpose;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthTokenProperties(Duration verificationTtl, Duration resetTtl) {
    public AuthTokenProperties {
        requirePositive(verificationTtl);
        requirePositive(resetTtl);
    }

    private static void requirePositive(Duration ttl) {
        if (ttl == null || ttl.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("Authentication token TTL must be at least 1ms");
        }
    }

    public Duration ttlFor(TokenPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> verificationTtl;
            case PASSWORD_RESET -> resetTtl;
        };
    }
}
