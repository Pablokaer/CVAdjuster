package com.resumetailor.payment.controller;

import com.resumetailor.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Receives webhook events from Stripe.
 *
 * Security model:
 *  - No authentication filter — Stripe servers do not send session cookies.
 *    The endpoint is in SecurityConfig's permitAll() list.
 *  - Authentication IS performed: every request must carry a valid HMAC-SHA256
 *    Stripe-Signature header computed over the raw body. Verification happens in
 *    PaymentService before any business logic runs.
 *  - Response body is always empty — never echo back payload details that could
 *    help an attacker understand the system's internals.
 *
 * HTTP status contract (Stripe uses this to decide retries):
 *  - 200  → event received and processed successfully
 *  - 400  → event rejected (bad signature, malformed payload) — Stripe will NOT retry
 *  - 500  → transient processing error — Stripe WILL retry with exponential back-off
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    /**
     * POST /api/webhook/stripe
     *
     * Uses {@code byte[]} instead of {@code String} for the body so that Spring's
     * message converter never re-encodes the bytes. The Stripe signature is computed
     * over the exact raw bytes Stripe sent; any charset conversion between receipt and
     * verification would corrupt the HMAC and cause legitimate events to be rejected.
     */
    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody byte[] rawPayload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        String payload = new String(rawPayload, StandardCharsets.UTF_8);

        try {
            paymentService.processWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok().build();

        } catch (SignatureVerificationException e) {
            // Bad signature — event is fake or tampered. Return 400 so Stripe does NOT retry.
            log.warn("SECURITY: Stripe-Signature verification failed — rejecting event");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

        } catch (Exception e) {
            // Unexpected processing error (e.g. DB unavailable). Return 500 so Stripe retries.
            // PaymentService is idempotent, so retries are safe.
            log.error("Webhook processing error — Stripe will retry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
