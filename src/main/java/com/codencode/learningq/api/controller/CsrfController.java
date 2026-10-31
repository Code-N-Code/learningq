package com.codencode.learningq.api.controller;

import com.codencode.learningq.api.dto.CsrfResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

    @GetMapping("/api/v1/auth/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        // Resolving the deferred token creates/persists its anonymous session.
        // Return Spring's request token, retaining its default masking protection.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }
}
