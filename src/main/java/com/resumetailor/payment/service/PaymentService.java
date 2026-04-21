package com.resumetailor.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.model.User;
import com.resumetailor.payment.config.StripeConfig;
import com.resumetailor.payment.dto.PaymentRequestDTO;
import com.resumetailor.payment.dto.PaymentResponseDTO;
import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.payment.entity.Order;
import com.resumetailor.payment.entity.OrderStatus;
import com.resumetailor.payment.exception.PaymentException;
import com.resumetailor.payment.repository.OrderRepository;
import com.resumetailor.service.UserService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final StripeConfig stripeConfig;
    private final UserService userService;

    /**
     * Creates a PENDING order and a Stripe Checkout session.
     * @param request  payment details including optional creditsToAdd
     * @param userEmail  email of the authenticated user making the purchase
     */
    @Transactional
    public PaymentResponseDTO createCheckoutSession(PaymentRequestDTO request, String userEmail) {
        // Resolve plan from the backend — price and credits are never accepted from the client
        CreditPlan plan = CreditPlan.fromId(request.getPlanId());
        User user = userService.getOrCreateByEmail(userEmail);

        Order order = Order.builder()
                .productName(plan.getProductName())
                .amount(plan.getAmount())
                .currency(plan.getCurrency())
                .status(OrderStatus.PENDING)
                .userId(user.getId())
                .creditsToAdd(plan.getCreditsToAdd())
                .build();
        order = orderRepository.save(order);

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeConfig.getSuccessUrl()
                            + "?success=true&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(stripeConfig.getCancelUrl() + "?canceled=true")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(plan.getCurrency())
                                                    .setUnitAmount(plan.getAmount())
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(plan.getProductName())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("order_id", order.getId().toString())
                    .build();

            Session session = Session.create(params);

            order.setStripeSessionId(session.getId());
            orderRepository.save(order);

            log.info("Checkout session {} created for order {} (user {})",
                    session.getId(), order.getId(), userEmail);

            return PaymentResponseDTO.builder()
                    .orderId(order.getId())
                    .stripeSessionId(session.getId())
                    .checkoutUrl(session.getUrl())
                    .status(OrderStatus.PENDING)
                    .build();

        } catch (StripeException e) {
            log.error("Stripe session creation failed for order {}: {}", order.getId(), e.getMessage());
            throw new PaymentException("Failed to create Stripe checkout session", e);
        }
    }

    /**
     * Validates and processes an incoming Stripe webhook event.
     * Credits are only added after the signature-verified webhook — never based on the redirect.
     */
    @Transactional
    public void processWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Webhook signature verification failed: {}", e.getMessage());
            throw new PaymentException("Invalid webhook signature", e);
        }

        log.info("Received Stripe event: {}", event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleSessionCompleted(event);
            case "checkout.session.expired"   -> handleSessionExpired(event);
            default -> log.debug("Unhandled event type: {}", event.getType());
        }
    }

    private void handleSessionCompleted(Event event) {
        String sessionId = extractSessionId(event);
        log.info("Processing checkout.session.completed for session {}", sessionId);

        orderRepository.findByStripeSessionId(sessionId).ifPresentOrElse(order -> {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            log.info("Order {} marked as PAID", order.getId());

            if (order.getUserId() != null
                    && order.getCreditsToAdd() != null
                    && order.getCreditsToAdd() > 0) {
                userService.addCredits(order.getUserId(), order.getCreditsToAdd());
                log.info("Credited {} credits to userId {}", order.getCreditsToAdd(), order.getUserId());
            } else {
                log.warn("Order {} has no userId or creditsToAdd — skipping credit grant", order.getId());
            }

        }, () -> log.warn("No order found for Stripe session {} — cannot grant credits", sessionId));
    }

    private void handleSessionExpired(Event event) {
        String sessionId = extractSessionId(event);

        orderRepository.findByStripeSessionId(sessionId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CANCELED);
                orderRepository.save(order);
                log.info("Order {} marked as CANCELED (session expired)", order.getId());
            }
        });
    }

    /**
     * Extracts the session ID from the raw webhook JSON.
     *
     * Using getRawJson() instead of getDataObjectDeserializer().getObject() avoids a silent
     * Optional.empty() that occurs when the Stripe API version in the event does not match
     * the version the SDK was compiled against — which would silently skip credit granting.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String extractSessionId(Event event) {
        try {
            JsonNode data = MAPPER.readTree(event.getDataObjectDeserializer().getRawJson());
            return data.get("id").asText();
        } catch (Exception e) {
            throw new PaymentException("Could not extract session ID from Stripe event: " + e.getMessage(), e);
        }
    }

    /**
     * Called when the user returns to the success URL after Stripe Checkout.
     * Verifies the session directly with the Stripe API and grants credits if
     * the order is still PENDING — this handles the case where the webhook has
     * not yet arrived (local dev, network delay, or misconfigured endpoint).
     *
     * @return true if credits were granted (or had already been granted), false otherwise
     */
    @Transactional
    public boolean reconcileSuccessfulSession(String sessionId) {
        Order order = orderRepository.findByStripeSessionId(sessionId).orElse(null);
        if (order == null) {
            log.warn("reconcile: no order found for session {}", sessionId);
            return false;
        }

        // Already processed by the webhook — nothing to do
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("reconcile: order {} already PAID — skipping", order.getId());
            return true;
        }

        // Ask Stripe directly whether the payment went through
        try {
            Session session = Session.retrieve(sessionId);
            if (!"paid".equals(session.getPaymentStatus())) {
                log.warn("reconcile: session {} paymentStatus={} — not crediting",
                        sessionId, session.getPaymentStatus());
                return false;
            }
        } catch (StripeException e) {
            log.error("reconcile: failed to retrieve session {} from Stripe: {}", sessionId, e.getMessage());
            return false;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        log.info("reconcile: order {} marked PAID", order.getId());

        if (order.getUserId() != null
                && order.getCreditsToAdd() != null
                && order.getCreditsToAdd() > 0) {
            userService.addCredits(order.getUserId(), order.getCreditsToAdd());
            log.info("reconcile: granted {} credits to userId {} for session {}",
                    order.getCreditsToAdd(), order.getUserId(), sessionId);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException("Order not found: " + orderId));
    }
}
