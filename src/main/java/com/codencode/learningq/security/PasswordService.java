package com.codencode.learningq.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final PasswordEncoder encoder;

    public PasswordService(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String encodeNewPassword(String password) {
        PasswordPolicy.validate(password);
        // Do not trim, normalize, log, or truncate a password.
        return encoder.encode(password);
    }

    public boolean matches(String password, String encodedPassword) {
        if (!PasswordPolicy.canVerify(password) || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        // Verification need not reapply the current minimum length: a future policy
        // increase must not automatically lock out accounts using an older policy.
        return encoder.matches(password, encodedPassword);
    }
}
