package com.codencode.learningq.service;

import java.sql.SQLIntegrityConstraintViolationException;

import com.codencode.learningq.api.dto.SignupRequest;
import com.codencode.learningq.api.dto.SignupResponse;
import com.codencode.learningq.security.PasswordService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTests {
    private static ValidatorFactory factory;
    @Mock PasswordService passwords;
    @Mock UserRegistrationWriter writer;
    private RegistrationService service;

    @BeforeAll
    static void setUpValidation() {
        factory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidation() {
        factory.close();
    }

    @BeforeEach
    void setUpService() {
        service = new RegistrationService(passwords, writer, factory.getValidator());
    }

    @Test
    void hashesBeforeWritingAndPassesOnlyEncodedPasswordToPersistence() {
        SignupRequest request = request();
        when(passwords.encodeNewPassword(request.password())).thenReturn("encoded-hash");

        assertEquals(SignupResponse.processed(), service.register(request));

        var order = inOrder(passwords, writer);
        order.verify(passwords).encodeNewPassword(request.password());
        order.verify(writer).create("Learner@Example.com", "learner@example.com", "encoded-hash", "Learner");
        order.verifyNoMoreInteractions();
    }

    @Test
    void duplicateEmailReturnsTheSameResponseAfterWriterFailure() {
        when(passwords.encodeNewPassword(request().password())).thenReturn("encoded-hash");
        doThrow(constraintFailure("users.uq_users_email_normalized"))
                .when(writer).create(anyString(), anyString(), anyString(), nullable(String.class));

        assertEquals(SignupResponse.processed(), service.register(request()));
    }

    @Test
    void unrelatedUniqueConstraintFailureIsNotReportedAsSuccessfulSignup() {
        when(passwords.encodeNewPassword(request().password())).thenReturn("encoded-hash");
        DataIntegrityViolationException failure = constraintFailure("PRIMARY");
        doThrow(failure).when(writer).create(anyString(), anyString(), anyString(), nullable(String.class));

        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.register(request())));
    }

    @Test
    void unspecifiedIntegrityFailuresAreNotSwallowed() {
        when(passwords.encodeNewPassword(request().password())).thenReturn("encoded-hash");
        var failure = new DataIntegrityViolationException("database failure");
        doThrow(failure).when(writer).create(anyString(), anyString(), anyString(), nullable(String.class));

        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.register(request())));
    }

    @Test
    void invalidRequestNeverHashesOrWritesAndUsesASafeExceptionMessage() {
        var failure = assertThrows(ConstraintViolationException.class,
                () -> service.register(new SignupRequest("bad-email", "private", " ")));
        assertFalse(failure.getMessage().contains("private"));
        verifyNoInteractions(passwords, writer);
    }

    private static SignupRequest request() {
        return new SignupRequest(" Learner@Example.com ", " test-password ", " Learner ");
    }

    private static DataIntegrityViolationException constraintFailure(String name) {
        var sql = new SQLIntegrityConstraintViolationException("duplicate", "23000", 1062);
        var hibernate = new org.hibernate.exception.ConstraintViolationException("insert failed", sql, name);
        return new DataIntegrityViolationException("insert failed", hibernate);
    }
}
