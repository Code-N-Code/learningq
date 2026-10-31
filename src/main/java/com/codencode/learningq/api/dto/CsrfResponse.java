package com.codencode.learningq.api.dto;

public record CsrfResponse(String token, String headerName) {

    @Override
    public String toString() {
        return "CsrfResponse[token=<redacted>, headerName=" + headerName + "]";
    }
}
