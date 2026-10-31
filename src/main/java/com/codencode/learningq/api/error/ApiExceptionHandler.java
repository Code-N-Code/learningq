package com.codencode.learningq.api.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        String detail = status.is5xxServerError()
                ? "An unexpected error occurred." : "The request could not be processed.";
        List<FieldViolation> errors = new ArrayList<>();

        if (exception instanceof MethodArgumentNotValidException invalid) {
            code = "VALIDATION_FAILED";
            detail = "One or more request fields are invalid.";
            for (ObjectError error : invalid.getBindingResult().getAllErrors()) {
                errors.add(violation(error));
            }
        } else if (exception instanceof HandlerMethodValidationException invalid && !status.is5xxServerError()) {
            code = "VALIDATION_FAILED";
            detail = "One or more request parameters are invalid.";
            // Parameter errors may contain rejected values in their arguments/messages.
            // Report only static constraint codes and a generic request location.
            invalid.getParameterValidationResults().forEach(result ->
                    result.getResolvableErrors().forEach(error ->
                            errors.add(violation(error instanceof FieldError field ? field.getField() : "request",
                                    error.getCodes()))));
        } else if (exception instanceof HttpMessageNotReadableException) {
            code = "MALFORMED_REQUEST";
            detail = "The request body is missing, malformed, or contains an invalid value.";
        } else if (exception instanceof TypeMismatchException) {
            code = "INVALID_PARAMETER";
            detail = "A request parameter has an invalid value.";
        }

        ProblemDetail problem = problem(status, code, detail);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors.stream().distinct()
                    .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                    .toList());
        }
        // Discard Spring's default body: exception details and validation messages
        // may contain submitted passwords, tokens or other rejected values.
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) throws Exception {
        // Authentication and authorization are handled by the security filter chain
        // in the next milestone, rather than converted to controller errors here.
        if (exception instanceof AuthenticationException || exception instanceof AccessDeniedException) {
            throw exception;
        }
        logger.error("Unexpected controller exception of type " + exception.getClass().getName());
        return super.handleExceptionInternal(exception,
                problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred."),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:learningq:error:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }

    private static FieldViolation violation(ObjectError error) {
        return violation(error instanceof FieldError field ? field.getField() : "request", error.getCodes());
    }

    private static FieldViolation violation(String field, String[] constraintCodes) {
        if (constraintCodes != null) {
            for (String constraint : constraintCodes) {
                switch (constraint) {
                    case "NotNull", "NotBlank", "NotEmpty":
                        return new FieldViolation(safeField(field), "REQUIRED", "This field is required.");
                    case "Email":
                        return new FieldViolation(safeField(field), "INVALID_EMAIL", "Enter a valid email address.");
                    case "Size":
                        return new FieldViolation(safeField(field), "INVALID_SIZE", "The field has an invalid length or size.");
                    case "Pattern":
                        return new FieldViolation(safeField(field), "INVALID_FORMAT", "The field has an invalid format.");
                    default:
                        break;
                }
            }
        }
        return new FieldViolation(safeField(field), "INVALID_VALUE", "The field has an invalid value.");
    }

    private static String safeField(String field) {
        // Map keys can be user input, so never echo bracket contents.
        String path = field.replaceAll("\\[[^]]*]", "[]");
        if (!path.matches("[A-Za-z0-9_.\\[\\]]+")) {
            return "request";
        }
        return path.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
