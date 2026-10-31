package com.codencode.learningq.api.controller;

import com.codencode.learningq.api.dto.SignupRequest;
import com.codencode.learningq.api.dto.SignupResponse;
import com.codencode.learningq.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final RegistrationService registration;

    public AuthController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(registration.register(request));
    }
}
