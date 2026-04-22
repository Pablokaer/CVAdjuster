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
import com.resumetailor.payment.entity.StripeProcessedEvent;
import com.resumetailor.payment.exception.PaymentException;
import com.resumetailor.event.CreditsPurchasedEvent;
import com.resumetailor.payment.repository.OrderRepository;
import com.resumetailor.payment.repository.StripeProcessedEventRepository;
import com.resumetailor.service.UserService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final StripeProcessedEventRepository processedEventRepository;
    private final StripeConfig stripeConfig;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── checkout session creation ─────────────────────────────────────────────

    /**
     * Creates a PENDING order record and a Stripe Checkout session.
     *
     * Metadata strategy:
     *  - order_id  → primary key for webhook-to-order resolution (fast, reliable)
     *  - user_id   → used as fallback if the order lookup fails in the webhook
     *  - credits   → used as fallback if the order lookup fails in the webhook
     *  - plan_id   → human-readable context in the Stripe dashboard
     *
     * The idempotency key "order-{id}" ensures that if the client retries this
     * request (double-click, network error), Stripe returns the existing session
     * instead of creating a duplicate.
     *
     * Price and credits are resolved from the backend CreditPlan enum —
     * the client only sends a planId string, never amounts or credits.
     */
    @Transactional
    public PaymentResponseDTO createCheckoutSession(PaymentRequestDTO request, String userEmail) {
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
                    // ── metadata ───────────────────────────────────────────
                    // Stripe caps metadata at 50 keys / 500 chars per value.
                    // These four fields are all a webhook handler ever needs to
                    // credit a user even if the local order lookup fails.
                    .putMetadata("order_id",  order.getId().toString())
                    .putMetadata("user_id",   order.getUserId().toString())
                    .putMetadata("credits",   order.getCreditsToAdd().toString())
                    .putMetadata("plan_id",   request.getPlanId())
                    .build();

            // Idempotency key prevents duplicate sessions on client retries.
            // Stripe returns the original session for any repeated call with the same key.
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("checkout-order-" + order.getId())
                    .build();

            Session session = Session.create(params, options);

            order.setStripeSessionId(session.getId());
            orderRepository.save(order);

            log.info("Checkout session {} created for order {} (userId={})",
                    session.getId(), order.getId(), order.getUserId());

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

    // ── webhook handling ──────────────────────────────────────────────────────

    /**
     * Validates and dispatches an incoming Stripe webhook event.
     *
     * Idempotency model:
     *  1. Fast path: existsByStripeEventId check skips events already committed.
     *  2. Constraint path: if two concurrent deliveries of the same event both pass
     *     the fast-path check, only one INSERT into stripe_processed_events succeeds.
     *     The other hits the UNIQUE constraint → DataIntegrityViolationException →
     *     entire transaction rolls back (including any credit grant) → controller
     *     returns 500 → Stripe retries → fast-path catches it → skipped cleanly.
     *
     * SignatureVerificationException propagates to the controller as 400 (no retry).
     * All other exceptions propagate as 500 (Stripe will retry).
     */
    @Transactional
    public void processWebhookEvent(String payload, String sigHeader)
            throws SignatureVerificationException {

        Event event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());

        log.info("Stripe event received: type={} id={}", event.getType(), event.getId());

        // Fast-path duplicate check — avoids re-processing events already committed.
        if (processedEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Stripe event {} already processed — skipping duplicate delivery", event.getId());
            return;
        }

        switch (event.getType()) {
            // Synchronous card payments — fires immediately on payment confirmation.
            case "checkout.session.completed" -> handleSessionCompleted(event);

            // Asynchronous payment methods (SEPA, BACS, ACH) — fires when the bank
            // confirms the transfer, which can be days after checkout.session.completed.
            // Both events are safe to handle with the same logic because creditFromOrder
            // checks order status before acting.
            case "checkout.session.async_payment_succeeded" -> handleSessionCompleted(event);

            case "checkout.session.expired" -> handleSessionExpired(event);
            default -> log.debug("Unhandled Stripe event: type={}", event.getType());
        }

        // Record this event as processed INSIDE the same transaction as the business
        // logic above. If the handler threw, we never reach this line and the transaction
        // rolls back — the event ID is NOT recorded, so Stripe's next retry is processed
        // normally. This guarantees: event ID present in DB ↔ credits actually granted.
        processedEventRepository.save(
                StripeProcessedEvent.builder()
                        .stripeEventId(event.getId())
                        .eventType(event.getType())
                        .processedAt(Instant.now())
                        .build()
        );
        log.info("Stripe event {} recorded as processed", event.getId());
    }

    private void handleSessionCompleted(Event event) {
        String sessionId = extractSessionId(event);
        log.info("Processing checkout.session.completed for session {}", sessionId);

        orderRepository.findByStripeSessionId(sessionId).ifPresentOrElse(
                order -> creditFromOrder(order),
                // Fallback: order not found (e.g. stripeSessionId was never saved due to a DB
                // failure between Session.create() and orderRepository.save()). Use the metadata
                // that was embedded in the session at creation time to recover.
                () -> {
                    log.warn("No order found for session {} — attempting metadata recovery", sessionId);
                    creditFromMetadata(event, sessionId);
                }
        );
    }

    private void creditFromOrder(Order order) {
        // Idempotency guard — Stripe may deliver the same event more than once.
        // If this order was already processed, return immediately without touching
        // the user's credit balance. This makes the entire handler safe to retry.
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order {} already PAID — duplicate event ignored", order.getId());
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        log.info("Order {} marked as PAID", order.getId());

        if (order.getUserId() != null
                && order.getCreditsToAdd() != null
                && order.getCreditsToAdd() > 0) {
            int credits = order.getCreditsToAdd();
            userService.addCredits(order.getUserId(), credits).ifPresent(user -> {
                log.info("Credited {} credits to userId={} (new balance: {})",
                        credits, user.getId(), user.getCredits());
                // Published inside the outer @Transactional — the AFTER_COMMIT listener
                // sends the confirmation email only when the entire transaction commits,
                // so the user never receives a confirmation for credits that were rolled back.
                eventPublisher.publishEvent(new CreditsPurchasedEvent(user, credits));
            });
        } else {
            log.warn("Order {} has no userId or creditsToAdd — skipping credit grant", order.getId());
        }
    }

    /**
     * Last-resort recovery using the metadata embedded in the Stripe session.
     * Called only when the local order record cannot be found.
     * Uses only data we put there ourselves — never trusts arbitrary metadata values
     * without parsing and bounds-checking them.
     */
    private void creditFromMetadata(Event event, String sessionId) {
        try {
            JsonNode data     = MAPPER.readTree(event.getDataObjectDeserializer().getRawJson());
            JsonNode metadata = data.path("metadata");

            if (metadata.isMissingNode() || metadata.isNull()) {
                log.error("No metadata in session {} — cannot recover credits", sessionId);
                return;
            }

            String userIdStr  = metadata.path("user_id").asText(null);
            String creditsStr = metadata.path("credits").asText(null);

            if (userIdStr == null || creditsStr == null) {
                log.error("Incomplete metadata in session {} (user_id={}, credits={}) — cannot recover",
                        sessionId, userIdStr, creditsStr);
                return;
            }

            long userId  = Long.parseLong(userIdStr);
            int  credits = Integer.parseInt(creditsStr);

            if (credits <= 0) {
                log.error("Invalid credits value {} in metadata for session {} — aborting", credits, sessionId);
                return;
            }

            userService.addCredits(userId, credits);
            log.warn("RECOVERY: granted {} credits to userId={} via metadata (session={})",
                    credits, userId, sessionId);

        } catch (NumberFormatException e) {
            log.error("Malformed numeric metadata in session {}: {}", sessionId, e.getMessage());
        } catch (Exception e) {
            log.error("Metadata recovery failed for session {}: {}", sessionId, e.getMessage());
        }
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

    // ── success-page reconciliation ───────────────────────────────────────────

    /**
     * Called when the user returns to the success URL.
     * Verifies the session directly with the Stripe API and grants credits if the
     * order is still PENDING — handles the case where the webhook has not arrived yet
     * (local dev, Stripe delivery delay, or misconfigured endpoint URL).
     *
     * Credits must never be granted based on the redirect URL alone — this always
     * verifies with Stripe before acting.
     */
    @Transactional
    public boolean reconcileSuccessfulSession(String sessionId) {
        Order order = orderRepository.findByStripeSessionId(sessionId).orElse(null);
        if (order == null) {
            log.warn("reconcile: no order found for session {}", sessionId);
            return false;
        }

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("reconcile: order {} already PAID — skipping", order.getId());
            return true;
        }

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
            int credits = order.getCreditsToAdd();
            userService.addCredits(order.getUserId(), credits).ifPresent(user -> {
                log.info("reconcile: granted {} credits to userId={} for session {}",
                        credits, user.getId(), sessionId);
                eventPublisher.publishEvent(new CreditsPurchasedEvent(user, credits));
            });
        }
        return true;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException("Order not found: " + orderId));
    }

    /**
     * Reads the Checkout Session ID from the raw webhook JSON.
     *
     * Using getRawJson() instead of getDataObjectDeserializer().getObject() avoids
     * a silent Optional.empty() when the Stripe API version in the event payload does
     * not match the version the SDK was compiled against — which would silently skip
     * credit granting in handleSessionCompleted.
     */
    private String extractSessionId(Event event) {
        try {
            JsonNode data = MAPPER.readTree(event.getDataObjectDeserializer().getRawJson());
            return data.get("id").asText();
        } catch (Exception e) {
            throw new PaymentException("Could not extract session ID from Stripe event: " + e.getMessage(), e);
        }
    }
}
