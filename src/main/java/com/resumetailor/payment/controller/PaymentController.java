package com.resumetailor.payment.controller;

import com.resumetailor.payment.dto.PaymentRequestDTO;
import com.resumetailor.payment.dto.PaymentResponseDTO;
import com.resumetailor.payment.entity.Order;
import com.resumetailor.payment.service.PaymentService;
import com.resumetailor.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payment/checkout
     * Creates a Stripe Checkout session and returns the redirect URL.
     *
     * Example request:
     * { "planId": "PRO" }
     *
     * Price, currency and credits come from the server-side CreditPlan enum;
     * the client never sends amounts or credit counts.
     */
    @PostMapping("/checkout")
    public ResponseEntity<PaymentResponseDTO> createCheckout(
            @Valid @RequestBody PaymentRequestDTO request,
            Authentication authentication) {

        String email = UserService.extractEmail(authentication);
        PaymentResponseDTO response = paymentService.createCheckoutSession(request, email);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/payment/orders/{id}
     * Retrieves the current status of one of the caller's own orders (for polling
     * from the client). Returns 404 for missing orders and for other users' orders.
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id, Authentication authentication) {
        String email = UserService.extractEmail(authentication);
        return paymentService.findOrderForUser(id, email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
