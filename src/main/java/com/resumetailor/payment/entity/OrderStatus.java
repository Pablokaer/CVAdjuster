package com.resumetailor.payment.entity;

public enum OrderStatus {
    PENDING,
    PAID,
    CANCELED,
    /** Delayed payment method (e.g. SEPA) was declined after checkout. */
    FAILED
}
