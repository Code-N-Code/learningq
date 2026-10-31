package com.codencode.learningq.api.controller;

import com.codencode.learningq.api.dto.CsrfResponse;
import com.codencode.learningq.api.dto.SignupRequest;
import com.codencode.learningq.api.dto.SignupResponse;
import com.codencode.learningq.api.error.ApiExceptionHandler;
import com.codencode.learningq.config.SecurityConfig;
import com.codencode.learningq.security.SecurityProblemHandler;
import com.codencode.learningq.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MVC validation and security filters; only registration's database work is mocked. */
@WebMvcTest(controllers = {AuthController.class, CsrfController.class})
@Import({SecurityConfig.class, SecurityProblemHandler.class, ApiExceptionHandler.class})
class AuthControllerTests {
    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String BODY = """
            {"email":" Learner@Example.com ","password":" private passphrase ","display_name":" Learner "}
            """;

    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @MockitoBean RegistrationService registration;

    @BeforeEach
    void registrationReturnsGenericResponse() {
        when(registration.register(any(SignupRequest.class))).thenReturn(SignupResponse.processed());
    }

    @Test
    void anonymousSignupWithRealSessionTokenReturnsOnlyGeneric202AndDoesNotLogIn() throws Exception {
        Bootstrap bootstrap = bootstrap();
        mvc.perform(signup(bootstrap).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().json("""
                        {"message":"Your signup request has been processed."}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));

        var request = ArgumentCaptor.forClass(SignupRequest.class);
        verify(registration).register(request.capture());
        assertEquals("Learner@Example.com", request.getValue().email());
        assertEquals(" private passphrase ", request.getValue().password());
        assertEquals("Learner", request.getValue().displayName());
        assertNull(bootstrap.session().getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get("/api/v1/users/me").session(bootstrap.session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void displayNameCanBeOmitted() throws Exception {
        mvc.perform(signup(bootstrap()).content("""
                        {"email":"learner@example.com","password":"private passphrase"}
                        """))
                .andExpect(status().isAccepted());
        var request = ArgumentCaptor.forClass(SignupRequest.class);
        verify(registration).register(request.capture());
        assertNull(request.getValue().displayName());
    }

    @Test
    void invalidSignupFieldsReturnSafe400BeforeCallingRegistration() throws Exception {
        Bootstrap bootstrap = bootstrap();
        String[][] cases = {
                {"{}", "email"},
                {"{\"email\":\"private-email-marker\",\"password\":\"private passphrase\"}", "email"},
                {"{\"email\":\"learner@example.com\",\"password\":\"private\"}", "password"},
                {"{\"email\":\"learner@example.com\",\"password\":null}", "password"},
                {"{\"email\":\"learner@example.com\",\"password\":\"private passphrase\",\"display_name\":\" \"}",
                        "display_name"}
        };
        for (String[] invalid : cases) {
            mvc.perform(signup(bootstrap).content(invalid[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[*].field", hasItem(invalid[1])))
                    .andExpect(content().string(not(containsString("private"))))
                    .andExpect(content().string(not(containsString("rejectedValue"))));
        }
        verifyNoInteractions(registration);
    }

    @Test
    void malformedMissingAndUnexpectedInputReturnSafe400() throws Exception {
        Bootstrap bootstrap = bootstrap();
        for (String body : new String[] {"", "null", "{\"password\":\"private-marker\",",
                "{\"password\":[\"private-marker\"]}",
                "{\"private-marker\":\"private-value\"}"}) {
            mvc.perform(signup(bootstrap).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                    .andExpect(content().string(not(containsString("private"))))
                    .andExpect(content().string(not(containsString("Exception"))));
        }
        verifyNoInteractions(registration);
    }

    @Test
    void signupStillRequiresCsrf() throws Exception {
        mvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(content().string(not(containsString("private"))));
        verifyNoInteractions(registration);
    }

    @Test
    void invalidCsrfIsRejectedBeforeRegistration() throws Exception {
        Bootstrap bootstrap = bootstrap();
        mvc.perform(post(SIGNUP).session(bootstrap.session())
                        .header(bootstrap.token().headerName(), "private-invalid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(content().string(not(containsString("private"))));
        verifyNoInteractions(registration);
    }

    @Test
    void tokenWithoutItsOriginalSessionIsRejected() throws Exception {
        Bootstrap first = bootstrap();
        Bootstrap second = bootstrap();
        for (MockHttpSession session : new MockHttpSession[] {null, second.session()}) {
            var request = post(SIGNUP).header(first.token().headerName(), first.token().token())
                    .contentType(MediaType.APPLICATION_JSON).content(BODY);
            if (session != null) {
                request.session(session);
            }
            mvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }
        verifyNoInteractions(registration);
    }

    @Test
    void publicPermissionCoversOnlyExactPostSignupRoute() throws Exception {
        Bootstrap bootstrap = bootstrap();
        mvc.perform(get(SIGNUP)).andExpect(status().isUnauthorized());
        mvc.perform(put(SIGNUP).session(bootstrap.session())
                        .header(bootstrap.token().headerName(), bootstrap.token().token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(SIGNUP + "/other").session(bootstrap.session())
                        .header(bootstrap.token().headerName(), bootstrap.token().token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/login").with(user("test-user")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(registration);
    }

    @Test
    void dependencyFailureIsNotReportedAsSuccessfulSignupAndDoesNotLeakDetails() throws Exception {
        when(registration.register(any(SignupRequest.class)))
                .thenThrow(new IllegalStateException("private-database-marker"));
        mvc.perform(signup(bootstrap()).content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("private"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private Bootstrap bootstrap() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return new Bootstrap(session, mapper.readValue(result.getResponse().getContentAsString(), CsrfResponse.class));
    }

    private static MockHttpServletRequestBuilder signup(Bootstrap bootstrap) {
        return post(SIGNUP).session(bootstrap.session())
                .header(bootstrap.token().headerName(), bootstrap.token().token())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private record Bootstrap(MockHttpSession session, CsrfResponse token) {
    }
}
