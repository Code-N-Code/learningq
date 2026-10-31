package com.codencode.learningq.api.dto;

import com.codencode.learningq.api.validation.ValidDisplayName;
import com.codencode.learningq.api.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 254)
        @Pattern(regexp = "[\\x20-\\x7E]*", message = "Email must use printable ASCII characters.") String email,
        @ValidPassword String password,
        @ValidDisplayName String displayName) {

    public SignupRequest {
        email = email == null ? null : email.trim();
        displayName = displayName == null ? null : displayName.trim();
        // Passwords are deliberately left untouched.
    }

    @Override
    public String toString() {
        return "SignupRequest[redacted]";
    }
}
