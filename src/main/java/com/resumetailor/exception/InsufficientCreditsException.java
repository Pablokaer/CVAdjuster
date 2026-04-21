package com.resumetailor.exception;

public class InsufficientCreditsException extends RuntimeException {
    public InsufficientCreditsException() {
        super("You don't have enough credits. Add credits to continue.");
    }
}
