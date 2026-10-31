package com.codencode.learningq.config;

import com.codencode.learningq.api.controller.CsrfController;
import com.codencode.learningq.api.dto.CsrfResponse;
import com.codencode.learningq.security.SecurityProblemHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.RequestCacheAwareFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CsrfController.class, SecurityConfigTests.RouteFixture.class})
@Import({SecurityConfig.class, SecurityProblemHandler.class, CsrfController.class,
        SecurityConfigTests.RouteFixture.class})
class SecurityConfigTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    SecurityFilterChain chain;

    @Autowired
    JsonMapper mapper;

    @Test
    void anonymousGetReturnsTokenAndHeaderNameWithoutCaching() throws Exception {
        bootstrap();
    }

    @Test
    void returnedTokenPassesCsrfWithItsSessionButDoesNotAuthenticate() throws Exception {
        MvcResult result = bootstrap();
        CsrfResponse token = tokenFrom(result);
        System.out.println("----->" + token);

        // This method/path is still denied: 401 (rather than CSRF_INVALID/403)
        // proves the real endpoint token passed CSRF and reached authorization.
        mvc.perform(post("/api/v1/auth/csrf").session(sessionFrom(result))
                        .header(token.headerName(), token.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void returnedTokenIsRejectedWithoutItsSession() throws Exception {
        CsrfResponse token = tokenFrom(bootstrap());

        mvc.perform(post("/api/v1/auth/csrf").header(token.headerName(), token.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void returnedTokenIsRejectedWithAnotherSessionsCookie() throws Exception {
        MvcResult first = bootstrap();
        MvcResult second = bootstrap();
        CsrfResponse token = tokenFrom(first);

        mvc.perform(post("/api/v1/auth/csrf").session(sessionFrom(second))
                        .header(token.headerName(), token.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(content().string(not(containsString(token.token()))));
    }

    @Test
    void refetchingTokenKeepsTheSessionAndPreviouslyIssuedTokenUsable() throws Exception {
        MvcResult first = bootstrap();
        MockHttpSession session = sessionFrom(first);
        MvcResult second = mvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        assertEquals(session.getId(), sessionFrom(second).getId());

        // Masked token strings can change per response; neither string should
        // invalidate the underlying token stored in the existing session.
        for (CsrfResponse token : new CsrfResponse[] {tokenFrom(first), tokenFrom(second)}) {
            mvc.perform(post("/api/v1/auth/csrf").session(session)
                            .header(token.headerName(), token.token()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void csrfResponseDoesNotIncludeTokenInToString() {
        CsrfResponse response = new CsrfResponse("private-token-marker", "X-CSRF-TOKEN");
        assertFalse(response.toString().contains("private-token-marker"));
    }

    @Test
    void unlistedRoutesAreDeniedForAnonymousAndAuthenticatedRequests() throws Exception {
        mvc.perform(get("/_test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("urn:learningq:error:authentication-required"))
                .andExpect(jsonPath("$.properties").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/_test/protected").with(user("test-user")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/auth/csrf/other"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRouteAllowanceIsLimitedToGet() throws Exception {
        // A valid CSRF token does not grant authorization for other HTTP methods.
        mvc.perform(post("/api/v1/auth/csrf").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void browserRequestsAreNotRedirectedToAFormLogin() throws Exception {
        mvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void anonymousWriteWithoutCsrfReturnsJson403BeforeAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/csrf").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"private-password-marker\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(content().string(not(containsString("private-password-marker"))))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void invalidCsrfTokenReturnsJson403WithoutEchoingToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest setup = new MockHttpServletRequest();
        setup.setSession(session);
        new HttpSessionCsrfTokenRepository().saveToken(
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "private-stored-token"),
                setup, new MockHttpServletResponse());

        mvc.perform(post("/api/v1/auth/csrf").session(session)
                        .header("X-CSRF-TOKEN", "private-submitted-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(content().string(not(containsString("private-stored-token"))))
                .andExpect(content().string(not(containsString("private-submitted-token"))))
                .andExpect(content().string(not(containsString("InvalidCsrfTokenException"))));
    }

    @Test
    void authenticationErrorDoesNotEchoCredentialsOrQueryParameters() throws Exception {
        mvc.perform(get("/_test/protected").param("token", "private-token-marker")
                        .header("Authorization", "Bearer private-password-marker"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(content().string(not(containsString("private-token-marker"))))
                .andExpect(content().string(not(containsString("private-password-marker"))))
                .andExpect(jsonPath("$.instance").doesNotExist());
    }

    @Test
    void authenticatedWriteWithCsrfStillRequiresRoutePermission() throws Exception {
        mvc.perform(post("/api/v1/auth/csrf").with(user("test-user")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void csrfStaysEnabledAndDefaultAuthenticationEndpointsAreDisabled() {
        assertTrue(chain.getFilters().stream().anyMatch(CsrfFilter.class::isInstance));
        assertFalse(chain.getFilters().stream().anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance));
        assertFalse(chain.getFilters().stream().anyMatch(BasicAuthenticationFilter.class::isInstance));
        assertFalse(chain.getFilters().stream().anyMatch(LogoutFilter.class::isInstance));
        assertFalse(chain.getFilters().stream().anyMatch(RequestCacheAwareFilter.class::isInstance));
    }

    private MvcResult bootstrap() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.header_name").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").doesNotExist())
                .andExpect(jsonPath("$.parameter_name").doesNotExist())
                .andReturn();
        assertNotNull(result.getRequest().getSession(false));
        return result;
    }

    private CsrfResponse tokenFrom(MvcResult result) throws Exception {
        return mapper.readValue(result.getResponse().getContentAsString(), CsrfResponse.class);
    }

    private static MockHttpSession sessionFrom(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @RestController
    static class RouteFixture {

        @GetMapping("/_test/protected")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void route() {
        }
    }
}
