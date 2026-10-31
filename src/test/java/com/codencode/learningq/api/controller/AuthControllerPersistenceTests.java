package com.codencode.learningq.api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.codencode.learningq.api.dto.CsrfResponse;
import com.codencode.learningq.repository.UserRepository;
import com.codencode.learningq.security.PasswordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full HTTP/filter/service flow with real commits; deletes only its own random test accounts. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerPersistenceTests {
    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String PASSWORD = " private test passphrase ";
    private final List<String> testEmails = new ArrayList<>();

    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void removeOnlyTestAccounts() {
        for (String email : testEmails) {
            users.findByEmailNormalized(email).ifPresent(user -> users.deleteById(user.getId()));
        }
    }

    @Test
    void newAndCaseVariantDuplicateSignupsReturnSame202WithoutModifyingAccountOrLoggingIn() throws Exception {
        String email = email();
        Bootstrap bootstrap = bootstrap();
        MvcResult first = signup(bootstrap, " " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD, " Original ");

        var original = users.findByEmailNormalized(email).orElseThrow();
        assertNotNull(original.getId());
        assertEquals(email.toUpperCase(Locale.ROOT), original.getEmail());
        assertEquals("Original", original.getDisplayName());
        assertNull(original.getEmailVerifiedAt());
        assertEquals(0L, original.getAuthVersion());
        assertNotNull(original.getCreatedAt());
        assertEquals(original.getCreatedAt(), original.getUpdatedAt());
        assertTrue(passwords.matches(PASSWORD, original.getPasswordHash()));
        assertFalse(passwords.matches(PASSWORD.trim(), original.getPasswordHash()));

        MvcResult duplicate = signup(bootstrap, email, "replacement passphrase", "Replacement");
        assertEquals(first.getResponse().getContentAsString(), duplicate.getResponse().getContentAsString());
        var unchanged = users.findByEmailNormalized(email).orElseThrow();
        assertEquals(original.getId(), unchanged.getId());
        assertEquals(original.getEmail(), unchanged.getEmail());
        assertEquals(original.getPasswordHash(), unchanged.getPasswordHash());
        assertEquals(original.getDisplayName(), unchanged.getDisplayName());
        assertEquals(original.getCreatedAt(), unchanged.getCreatedAt());
        assertEquals(original.getUpdatedAt(), unchanged.getUpdatedAt());
        assertNull(unchanged.getEmailVerifiedAt());
        assertEquals(0L, unchanged.getAuthVersion());
        assertEquals(1L, accountCount(email));
        assertNull(bootstrap.session().getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get("/api/v1/users/me").session(bootstrap.session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputAndMissingCsrfDoNotCreateAnAccount() throws Exception {
        String email = email();
        Bootstrap bootstrap = bootstrap();
        mvc.perform(post(SIGNUP).session(bootstrap.session())
                        .header(bootstrap.token().headerName(), bootstrap.token().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "short", "Learner")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, PASSWORD, "Learner")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertEquals(0L, accountCount(email));
    }

    @Test
    void concurrentHttpSignupsFromSeparateSessionsCreateOneAccountAndReturnIdentical202() throws Exception {
        String email = email();
        Bootstrap firstSession = bootstrap();
        Bootstrap secondSession = bootstrap();
        CountDownLatch start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> {
                start.await();
                return signup(firstSession, email, PASSWORD, "First").getResponse().getContentAsString();
            });
            var second = workers.submit(() -> {
                start.await();
                return signup(secondSession, email.toUpperCase(Locale.ROOT), "second test passphrase", "Second")
                        .getResponse().getContentAsString();
            });
            start.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertEquals(1L, accountCount(email));
        var user = users.findByEmailNormalized(email).orElseThrow();
        assertTrue(passwords.matches("First".equals(user.getDisplayName()) ? PASSWORD : "second test passphrase",
                user.getPasswordHash()));
        assertNull(user.getEmailVerifiedAt());
    }

    private MvcResult signup(Bootstrap bootstrap, String email, String password, String name) throws Exception {
        return mvc.perform(post(SIGNUP).session(bootstrap.session())
                        .header(bootstrap.token().headerName(), bootstrap.token().token())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, password, name)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"message":"Your signup request has been processed."}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andReturn();
    }

    private String body(String email, String password, String name) {
        return mapper.writeValueAsString(Map.of("email", email, "password", password, "display_name", name));
    }

    private Bootstrap bootstrap() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return new Bootstrap(session, mapper.readValue(result.getResponse().getContentAsString(), CsrfResponse.class));
    }

    private long accountCount(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email_normalized = ?", Long.class, email);
    }

    private String email() {
        String email = "signup-http-test-" + UUID.randomUUID() + "@example.com";
        testEmails.add(email);
        return email;
    }

    private record Bootstrap(MockHttpSession session, CsrfResponse token) {
    }
}
