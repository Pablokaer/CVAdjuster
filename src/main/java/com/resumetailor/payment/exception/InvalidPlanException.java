package com.resumetailor.payment.exception;

/** The client asked for a credit plan that does not exist. */
public class InvalidPlanException extends PaymentException {

    public InvalidPlanException(String planId) {
        super("Invalid plan ID: " + planId);
    }
}
