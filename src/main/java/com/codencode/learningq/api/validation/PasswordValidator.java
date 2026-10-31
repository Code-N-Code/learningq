package com.codencode.learningq.api.validation;

import com.codencode.learningq.security.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        try {
            PasswordPolicy.validate(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
