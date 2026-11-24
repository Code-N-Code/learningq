package com.codencode.learningq.service;

/** Generic failure: never disclose the submitted token or reason for rejection. */
public class InvalidAuthTokenException extends RuntimeException {
    public InvalidAuthTokenException() {
        super("Authentication token is invalid or unavailable.");
    }
}
