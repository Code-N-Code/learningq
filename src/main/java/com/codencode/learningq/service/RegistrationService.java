package com.codencode.learningq.service;

import java.util.Objects;

import com.codencode.learningq.api.dto.SignupRequest;
import com.codencode.learningq.api.dto.SignupResponse;
import com.codencode.learningq.security.PasswordService;
import com.codencode.learningq.util.EmailNormalizer;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final PasswordService passwords;
    private final UserRegistrationWriter writer;
    private final Validator validator;

    public RegistrationService(PasswordService passwords, UserRegistrationWriter writer, Validator validator) {
        this.passwords = passwords;
        this.writer = writer;
        this.validator = validator;
    }

    // Refuse an ambient transaction: duplicate handling must happen after the
    // writer's transaction has completed or rolled back, never inside it.
    @Transactional(propagation = Propagation.NEVER)
    public SignupResponse register(SignupRequest request) {
        Objects.requireNonNull(request, "Signup request is required");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("Invalid signup request", violations);
        }

        String normalized = EmailNormalizer.normalize(request.email());
        // Do the same expensive hash work on new and duplicate signups. Avoid an
        // early existsByEmailNormalized shortcut that leaks account existence.
        String encoded = passwords.encodeNewPassword(request.password());
        try {
            writer.create(request.email(), normalized, encoded, request.displayName());
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateEmail(exception)) {
                throw exception;
            }
            // The failed writer transaction has already rolled back. No existing
            // account is loaded or mutated, and the public response is identical.
        }
        return SignupResponse.processed();
    }

    private static boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null && violation.getSQLException().getErrorCode() == 1062) {
                    name = name.replace("`", "").replace("\"", "");
                    name = name.substring(name.lastIndexOf('.') + 1);
                    return name.equalsIgnoreCase("uq_users_email_normalized");
                }
            }
        }
        return false;
    }
}
