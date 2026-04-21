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
     * {
     *   "productName": "Pro – 20 Credits",
     *   "amount": 1499,
     *   "currency": "usd",
     *   "creditsToAdd": 20
     * }
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
     * Retrieves the current status of an order (for polling from the client).
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getOrderById(id));
    }
}
