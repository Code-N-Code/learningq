package com.codencode.learningq.api.error;

/** Public validation information only; never include rejected values or exceptions. */
public record FieldViolation(String field, String code, String message) {
}
