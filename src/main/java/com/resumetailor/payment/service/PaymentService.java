package com.resumetailor.payment.service;

import com.resumetailor.model.User;
import com.resumetailor.payment.config.StripeConfig;
import com.resumetailor.payment.dto.PaymentRequestDTO;
import com.resumetailor.payment.dto.PaymentResponseDTO;
import com.resumetailor.payment.dto.SessionFacts;
import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.payment.entity.Order;
import com.resumetailor.payment.entity.OrderStatus;
import com.resumetailor.payment.entity.StripeProcessedEvent;
import com.resumetailor.payment.exception.PaymentException;
import com.resumetailor.event.CreditsPurchasedEvent;
import com.resumetailor.payment.repository.OrderRepository;
import com.resumetailor.payment.repository.StripeProcessedEventRepository;
import com.resumetailor.repository.UserRepository;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final StripeProcessedEventRepository processedEventRepository;
    private final StripeConfig stripeConfig;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ── checkout session creation ─────────────────────────────────────────────

    /**
     * Creates a PENDING order record and a Stripe Checkout session.
     *
     * Metadata strategy:
     *  - order_id  → primary key for webhook-to-order resolution (fast, reliable)
     *  - user_id   → used to rebuild the order if the order lookup fails in the webhook
     *  - credits   → cross-checked against the plan during that recovery
     *  - plan_id   → resolves the plan during recovery; also context in the Stripe dashboard
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
            // completed fires at the end of checkout. For delayed payment methods (SEPA,
            // bank transfers) the session may still be unpaid at that point; the money
            // arrives later with async_payment_succeeded. fulfil() checks payment_status,
            // so both events share one handler.
            case "checkout.session.completed",
                 "checkout.session.async_payment_succeeded" -> handleSessionPaid(event);

            case "checkout.session.async_payment_failed" -> handleSessionFailed(event);
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


    private void handleSessionPaid(Event event) {
        SessionFacts facts = SessionFacts.fromEvent(event);
        log.info("Processing {} for session {} (payment_status={})",
                event.getType(), facts.sessionId(), facts.paymentStatus());

        Optional<Order> order = orderRepository.findByStripeSessionIdForUpdate(facts.sessionId());
        if (order.isPresent()) {
            fulfil(order.get(), facts);
        } else {
            // Order not found (e.g. stripeSessionId was never saved due to a DB failure
            // between Session.create() and orderRepository.save()). Rebuild it from the
            // metadata embedded in the session at creation time.
            log.warn("No order found for session {} — attempting metadata recovery", facts.sessionId());
            recoverOrderFromMetadata(facts);
        }
    }

    private void handleSessionFailed(Event event) {
        String sessionId = SessionFacts.fromEvent(event).sessionId();

        orderRepository.findByStripeSessionIdForUpdate(sessionId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.FAILED);
                orderRepository.save(order);
                log.info("Order {} marked as FAILED (async payment failed)", order.getId());
            }
        });
    }

    private void handleSessionExpired(Event event) {
        String sessionId = SessionFacts.fromEvent(event).sessionId();

        orderRepository.findByStripeSessionIdForUpdate(sessionId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CANCELED);
                orderRepository.save(order);
                log.info("Order {} marked as CANCELED (session expired)", order.getId());
            }
        });
    }

    /**
     * Last-resort recovery when no local order matches the session. Never credits
     * straight from metadata: the values must describe a real plan and match what
     * Stripe actually charged. A new PENDING order is then saved and fulfilled
     * through the same path as every other order.
     *
     * saveAndFlush() makes a concurrent duplicate hit the UNIQUE constraint on
     * stripe_session_id; that transaction rolls back and Stripe retries, by which
     * time the order exists and the normal path sees it as already PAID.
     */
    private void recoverOrderFromMetadata(SessionFacts facts) {
        String sessionId = facts.sessionId();

        if (!facts.isPaid()) {
            log.warn("RECOVERY: session {} is not paid (payment_status={}) — nothing to recover",
                    sessionId, facts.paymentStatus());
            return;
        }

        CreditPlan plan;
        long userId;
        int credits;
        try {
            plan    = CreditPlan.fromId(facts.metadata().get("plan_id"));
            userId  = Long.parseLong(facts.metadata().get("user_id"));
            credits = Integer.parseInt(facts.metadata().get("credits"));
        } catch (PaymentException | NumberFormatException e) {
            log.error("RECOVERY: invalid metadata in session {} ({}) — cannot recover: {}",
                    sessionId, facts.metadata(), e.getMessage());
            return;
        }

        if (credits != plan.getCreditsToAdd()
                || facts.amountTotal() == null
                || facts.amountTotal() != plan.getAmount()
                || !plan.getCurrency().equalsIgnoreCase(facts.currency())) {
            log.error("SECURITY: session {} does not match plan {} (credits={}, amount={} {}; "
                            + "expected credits={}, amount={} {}) — not crediting",
                    sessionId, plan, credits, facts.amountTotal(), facts.currency(),
                    plan.getCreditsToAdd(), plan.getAmount(), plan.getCurrency());
            return;
        }

        Order order = orderRepository.saveAndFlush(Order.builder()
                .productName(plan.getProductName())
                .amount(plan.getAmount())
                .currency(plan.getCurrency())
                .status(OrderStatus.PENDING)
                .userId(userId)
                .creditsToAdd(plan.getCreditsToAdd())
                .stripeSessionId(sessionId)
                .build());
        log.warn("RECOVERY: rebuilt order {} for session {} from metadata (userId={})",
                order.getId(), sessionId, userId);

        fulfil(order, facts);
    }

    // ── fulfilment ────────────────────────────────────────────────────────────

    /**
     * The single place where a purchase turns into credits; used by the webhook and
     * the success-page reconciliation. The caller must hold the row lock on the order
     * (findByStripeSessionIdForUpdate) or have just inserted it, so concurrent callers
     * are serialized and only the first one sees PENDING.
     *
     * Credits are granted only when the order is PENDING, Stripe reports the session
     * as paid, and the charged amount and currency match the order.
     *
     * @return true if the order is PAID after this call (just now or already before)
     */
    public boolean fulfil(Order lockedOrder, SessionFacts facts) {
        if (lockedOrder.getStatus() == OrderStatus.PAID) {
            log.info("Order {} already PAID — nothing to do", lockedOrder.getId());
            return true;
        }
        if (lockedOrder.getStatus() != OrderStatus.PENDING) {
            log.warn("Order {} is {} — not crediting session {}",
                    lockedOrder.getId(), lockedOrder.getStatus(), facts.sessionId());
            return false;
        }
        if (!facts.isPaid()) {
            log.info("Session {} payment_status={} — order {} stays PENDING until payment arrives",
                    facts.sessionId(), facts.paymentStatus(), lockedOrder.getId());
            return false;
        }
        if (!lockedOrder.getAmount().equals(facts.amountTotal())
                || !lockedOrder.getCurrency().equalsIgnoreCase(facts.currency())) {
            log.error("SECURITY: session {} charged {} {} but order {} expects {} {} — not crediting",
                    facts.sessionId(), facts.amountTotal(), facts.currency(),
                    lockedOrder.getId(), lockedOrder.getAmount(), lockedOrder.getCurrency());
            return false;
        }

        lockedOrder.setStatus(OrderStatus.PAID);
        orderRepository.save(lockedOrder);
        log.info("Order {} marked as PAID", lockedOrder.getId());

        if (lockedOrder.getUserId() != null
                && lockedOrder.getCreditsToAdd() != null
                && lockedOrder.getCreditsToAdd() > 0) {
            int credits = lockedOrder.getCreditsToAdd();
            userService.addCredits(lockedOrder.getUserId(), credits).ifPresent(user -> {
                log.info("Credited {} credits to userId={} (new balance: {})",
                        credits, user.getId(), user.getCredits());
                // Published inside the outer @Transactional — the AFTER_COMMIT listener
                // sends the confirmation email only when the entire transaction commits,
                // so the user never receives a confirmation for credits that were rolled back.
                eventPublisher.publishEvent(new CreditsPurchasedEvent(user, credits));
            });
        } else {
            log.warn("Order {} has no userId or creditsToAdd — skipping credit grant", lockedOrder.getId());
        }
        return true;
    }

    // ── success-page reconciliation ───────────────────────────────────────────

    /**
     * Called when the user returns to the success URL.
     * Verifies the session directly with the Stripe API and fulfils the order if it
     * is still PENDING — handles the case where the webhook has not arrived yet
     * (local dev, Stripe delivery delay, or misconfigured endpoint URL).
     *
     * Credits must never be granted based on the redirect URL alone — this always
     * verifies with Stripe before acting.
     */
    @Transactional
    public boolean reconcileSuccessfulSession(String sessionId) {
        Order order = orderRepository.findByStripeSessionIdForUpdate(sessionId).orElse(null);
        if (order == null) {
            log.warn("reconcile: no order found for session {}", sessionId);
            return false;
        }

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("reconcile: order {} already PAID — skipping", order.getId());
            return true;
        }

        Session session;
        try {
            session = Session.retrieve(sessionId);
        } catch (StripeException e) {
            log.error("reconcile: failed to retrieve session {} from Stripe: {}", sessionId, e.getMessage());
            return false;
        }
        return fulfil(order, SessionFacts.fromSession(session));
    }

    // ── order lookup ──────────────────────────────────────────────────────────

    /**
     * Returns the order only if it belongs to the user with the given email.
     * Unknown orders, unknown users and other users' orders are all empty, so a
     * caller cannot tell someone else's order apart from a missing one.
     */
    @Transactional(readOnly = true)
    public Optional<Order> findOrderForUser(Long orderId, String email) {
        return userRepository.findByEmail(email)
                .flatMap(user -> orderRepository.findById(orderId)
                        .filter(order -> user.getId().equals(order.getUserId())));
    }
}
