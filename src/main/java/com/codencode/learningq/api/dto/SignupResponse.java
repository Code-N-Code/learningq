package com.codencode.learningq.api.dto;

public record SignupResponse(String message) {
    public static SignupResponse processed() {
        return new SignupResponse("Your signup request has been processed.");
    }
}
