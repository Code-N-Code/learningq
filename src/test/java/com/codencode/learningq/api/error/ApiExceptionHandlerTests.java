package com.codencode.learningq.api.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller-layer checks only. Security filter behavior belongs to milestone 6.
@WebMvcTest(controllers = ApiExceptionHandlerTests.ValidationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, ApiExceptionHandlerTests.ValidationController.class})
class ApiExceptionHandlerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void validRequestsAndResponsesUseSnakeCase() throws Exception {
        mvc.perform(post("/_test/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"learner@example.com","password":"safe","display_name":"Learner","age":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("Learner"))
                .andExpect(jsonPath("$.displayName").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void validationErrorsUseSafeMessagesAndSnakeCaseFieldNames() throws Exception {
        mvc.perform(post("/_test/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"private-email-marker","password":"private-password-marker",
                                 "display_name":"Name exceeding limit","age":20}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("display_name"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_SIZE"))
                .andExpect(content().string(not(containsString("private-email-marker"))))
                .andExpect(content().string(not(containsString("private-password-marker"))))
                .andExpect(content().string(not(containsString("unsafe-validator-message"))))
                .andExpect(content().string(not(containsString("rejectedValue"))));
    }

    @Test
    void missingRequiredFieldsAreReportedWithoutFrameworkInternals() throws Exception {
        mvc.perform(post("/_test/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"))
                .andExpect(content().string(not(containsString("BindingResult"))));
    }

    @Test
    void malformedJsonDoesNotExposeBodyOrParserMessages() throws Exception {
        mvc.perform(post("/_test/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"private-token-marker\","))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("private-token-marker"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void invalidJsonTypesAreTreatedAsMalformedRequests() throws Exception {
        mvc.perform(post("/_test/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":\"private-token-marker\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("private-token-marker"))));
    }

    @Test
    void missingRequestBodyUsesTheMalformedRequestContract() throws Exception {
        mvc.perform(post("/_test/profile").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unexpectedJsonFieldsAreRejectedWithoutEchoingThem() throws Exception {
        mvc.perform(post("/_test/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"private-token-marker\":\"private-password-marker\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("private-token-marker"))))
                .andExpect(content().string(not(containsString("private-password-marker"))));
    }

    @Test
    void unsupportedMethodsKeepTheirHttpStatusAndAllowHeader() throws Exception {
        mvc.perform(get("/_test/profile"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("REQUEST_FAILED"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidQueryParametersDoNotExposeTheirValues() throws Exception {
        mvc.perform(get("/_test/limit").param("limit", "private-token-marker"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(content().string(not(containsString("private-token-marker"))));
    }

    @Test
    void methodParameterConstraintsUseTheValidationContract() throws Exception {
        mvc.perform(get("/_test/limit").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    }

    @Test
    void unexpectedErrorsDoNotExposeExceptionDetails() throws Exception {
        mvc.perform(get("/_test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("private-token-marker"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @RestController
    static class ValidationController {

        @PostMapping("/_test/profile")
        ProfileView profile(@Valid @RequestBody ProfileRequest request) {
            return new ProfileView(request.displayName());
        }

        @GetMapping("/_test/limit")
        int limit(@RequestParam("limit") @Min(1) int limit) {
            return limit;
        }

        @GetMapping("/_test/failure")
        void failure() {
            throw new IllegalStateException("private-token-marker");
        }
    }

    record ProfileRequest(@NotBlank @Email String email,
                          @NotBlank @Pattern(regexp = "safe", message = "unsafe-validator-message") String password,
                          @Size(max = 10) String displayName,
                          Integer age) {
        @Override
        public String toString() {
            return "ProfileRequest[redacted]";
        }
    }

    record ProfileView(String displayName) {
    }
}
