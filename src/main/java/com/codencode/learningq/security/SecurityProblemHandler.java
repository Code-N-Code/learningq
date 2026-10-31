package com.codencode.learningq.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static com.codencode.learningq.api.error.ApiProblems.problem;

@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper mapper;

    public SecurityProblemHandler(JsonMapper mapper) {
        // Filters write outside MVC's message converters. Apply the same mixin
        // so extension properties such as "code" remain top-level JSON fields.
        this.mapper = mapper.rebuild()
                .addMixIn(org.springframework.http.ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (exception instanceof CsrfException) {
            write(response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "The CSRF token is missing or invalid.");
        } else {
            write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access to this resource is denied.");
        }
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        // No sendError(), exception messages, submitted values, or request URLs:
        // return the JSON directly rather than dispatching to an HTML error page.
        mapper.writeValue(response.getOutputStream(), problem(status, code, detail));
    }
}
