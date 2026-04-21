package com.resumetailor.payment.controller;

import com.resumetailor.payment.exception.PaymentException;
import com.resumetailor.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    /**
     * POST /api/webhook/stripe
     * Receives and verifies Stripe webhook events.
     * Must be publicly accessible (no auth) and receive the raw request body.
     */
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            paymentService.processWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok("Event processed");
        } catch (PaymentException e) {
            log.warn("Webhook processing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
