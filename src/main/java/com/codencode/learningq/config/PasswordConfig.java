package com.codencode.learningq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        // Salt bytes, hash bytes, lanes, memory KiB, iterations.
        // OWASP Argon2id baseline; benchmark on deployment hardware before release.
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }
}
