package com.resumetailor.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        // Generic message — never tell the caller whether the token existed or was expired
        super("Invalid or expired password reset link.");
    }
}
