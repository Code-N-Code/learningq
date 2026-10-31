package com.codencode.learningq.api.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DisplayNameValidator.class)
public @interface ValidDisplayName {
    String message() default "Display name must contain 1 to 80 Unicode characters when supplied.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
