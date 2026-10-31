package com.codencode.learningq.api.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DisplayNameValidator implements ConstraintValidator<ValidDisplayName, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return !trimmed.isBlank() && trimmed.length() <= 160
                && trimmed.codePointCount(0, trimmed.length()) <= 80
                && StandardCharsets.UTF_8.newEncoder().canEncode(trimmed);
    }
}
