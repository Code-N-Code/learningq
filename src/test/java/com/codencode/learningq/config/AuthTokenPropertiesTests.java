package com.codencode.learningq.config;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class AuthTokenPropertiesTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsReadableDurations() {
        context.withPropertyValues("app.auth.verification-ttl=12h", "app.auth.reset-ttl=15m")
                .run(result -> {
                    assertNull(result.getStartupFailure());
                    var properties = result.getBean(AuthTokenProperties.class);
                    assertEquals(Duration.ofHours(12), properties.verificationTtl());
                    assertEquals(Duration.ofMinutes(15), properties.resetTtl());
                });
    }

    @Test
    void failsStartupWhenLifetimeIsMissingOrNonpositive() {
        context.withPropertyValues("app.auth.verification-ttl=24h")
                .run(result -> assertNotNull(result.getStartupFailure()));
        context.withPropertyValues("app.auth.verification-ttl=24h", "app.auth.reset-ttl=0s")
                .run(result -> assertNotNull(result.getStartupFailure()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthTokenProperties.class)
    static class PropertiesConfig {}
}
