package com.resumetailor.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentRequestDTO {

    /**
     * Plan identifier sent by the frontend: STARTER | PRO | PREMIUM.
     * Price, credits and currency are resolved on the backend from CreditPlan enum
     * and never accepted from the client.
     */
    @NotBlank(message = "Plan ID is required")
    private String planId;
}
