package com.resumetailor.payment;

import com.resumetailor.exception.InsufficientCreditsException;
import com.resumetailor.model.User;
import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.payment.entity.Order;
import com.resumetailor.payment.entity.OrderStatus;
import com.resumetailor.payment.repository.OrderRepository;
import com.resumetailor.payment.service.PaymentService;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.UserService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * End-to-end payment fulfilment against a real PostgreSQL (schema created by Flyway).
 * Row locks and atomic UPDATEs cannot be verified with mocks, so this runs only when
 * IT_DB_URL is set; CI has no database and skips it.
 *
 * Webhooks are signed with a test secret and fed to PaymentService directly —
 * no calls reach Stripe.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
class PaymentFlowIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_integration_test_secret";
    private static final CreditPlan PLAN = CreditPlan.STARTER;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("IT_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("IT_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("IT_DB_PASSWORD"));
        registry.add("stripe.secret-key", () -> "sk_test_integration");
        registry.add("stripe.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("stripe.success-url", () -> "http://localhost/credits");
        registry.add("stripe.cancel-url", () -> "http://localhost/credits");
        registry.add("app.base-url", () -> "http://localhost");
        registry.add("app.mail.from", () -> "test@example.com");
        registry.add("openai.api.key", () -> "test-key");
        registry.add("spring.mail.username", () -> "test@example.com");
        registry.add("spring.mail.password", () -> "test");
        registry.add("spring.mail.test-connection", () -> "false");
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @MockitoBean private JavaMailSender mailSender;

    @Autowired private PaymentService paymentService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;

    @BeforeEach
    void stubMail() {
        // The purchase confirmation email is sent after commit; give it a real message to fill
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((jakarta.mail.Session) null));
    }

    // ── webhook fulfilment ────────────────────────────────────────────────────

    @Test
    void paidCardSessionCreditsUser() throws Exception {
        User user = newUser(0);
        Order order = newOrder(user);

        sendSessionEvent("checkout.session.completed", order.getStripeSessionId(), "paid", PLAN.getAmount());

        assertThat(creditsOf(user)).isEqualTo(PLAN.getCreditsToAdd());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void unpaidSessionIsCreditedOnlyWhenAsyncPaymentSucceeds() throws Exception {
        User user = newUser(0);
        Order order = newOrder(user);

        sendSessionEvent("checkout.session.completed", order.getStripeSessionId(), "unpaid", PLAN.getAmount());

        assertThat(creditsOf(user)).isZero();
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PENDING);

        sendSessionEvent("checkout.session.async_payment_succeeded", order.getStripeSessionId(), "paid", PLAN.getAmount());

        assertThat(creditsOf(user)).isEqualTo(PLAN.getCreditsToAdd());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void unpaidSessionThenAsyncFailureEndsFailedWithoutCredits() throws Exception {
        User user = newUser(0);
        Order order = newOrder(user);

        sendSessionEvent("checkout.session.completed", order.getStripeSessionId(), "unpaid", PLAN.getAmount());
        sendSessionEvent("checkout.session.async_payment_failed", order.getStripeSessionId(), "unpaid", PLAN.getAmount());

        assertThat(creditsOf(user)).isZero();
        assertThat(statusOf(order)).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void wrongAmountIsNotCredited() throws Exception {
        User user = newUser(0);
        Order order = newOrder(user);

        sendSessionEvent("checkout.session.completed", order.getStripeSessionId(), "paid", 1L);

        assertThat(creditsOf(user)).isZero();
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void forgedSignatureIsRejected() {
        User user = newUser(0);
        Order order = newOrder(user);
        String payload = sessionEventPayload("checkout.session.completed", order.getStripeSessionId(), "paid", PLAN.getAmount());

        assertThrows(SignatureVerificationException.class,
                () -> paymentService.processWebhookEvent(payload, signatureHeader(payload, "whsec_forged")));

        assertThat(creditsOf(user)).isZero();
    }

    // ── concurrency ───────────────────────────────────────────────────────────

    /**
     * Two different paid events for one session (completed + async_payment_succeeded,
     * or the webhook racing the success page) must credit once. Repeated over several
     * fresh orders so that, without the row lock, at least one round double-credits.
     */
    @Test
    void concurrentPaidEventsForSameSessionCreditOnce() throws Exception {
        int rounds = 10;
        List<User> users = new ArrayList<>();

        for (int i = 0; i < rounds; i++) {
            User user = newUser(0);
            Order order = newOrder(user);
            users.add(user);

            String a = sessionEventPayload("checkout.session.completed", order.getStripeSessionId(), "paid", PLAN.getAmount());
            String b = sessionEventPayload("checkout.session.async_payment_succeeded", order.getStripeSessionId(), "paid", PLAN.getAmount());

            runConcurrently(List.of(
                    () -> { paymentService.processWebhookEvent(a, signatureHeader(a, WEBHOOK_SECRET)); return true; },
                    () -> { paymentService.processWebhookEvent(b, signatureHeader(b, WEBHOOK_SECRET)); return true; }));
        }

        for (User user : users) {
            assertThat(creditsOf(user))
                    .as("credits for userId=%d", user.getId())
                    .isEqualTo(PLAN.getCreditsToAdd());
        }
    }

    @Test
    void parallelDeductionsSpendTheLastCreditOnlyOnce() throws Exception {
        User user = newUser(1);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                try {
                    userService.deductCredit(user.getEmail());
                    return true;
                } catch (InsufficientCreditsException e) {
                    return false;
                }
            });
        }

        List<Boolean> results = runConcurrently(tasks);

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(creditsOf(user)).isZero();
    }

    @Test
    void parallelAdditionsAreNotLost() throws Exception {
        User user = newUser(0);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> userService.addCredits(user.getId(), 1).isPresent());
        }

        assertThat(runConcurrently(tasks)).containsOnly(true);
        assertThat(creditsOf(user)).isEqualTo(10);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User newUser(int credits) {
        User user = new User();
        user.setEmail("it-" + UUID.randomUUID() + "@example.com");
        user.setProvider("LOCAL");
        user.setCredits(credits);
        return userRepository.save(user);
    }

    private Order newOrder(User user) {
        return orderRepository.save(Order.builder()
                .productName(PLAN.getProductName())
                .amount(PLAN.getAmount())
                .currency(PLAN.getCurrency())
                .status(OrderStatus.PENDING)
                .userId(user.getId())
                .creditsToAdd(PLAN.getCreditsToAdd())
                .stripeSessionId("cs_test_" + UUID.randomUUID())
                .build());
    }

    private int creditsOf(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getCredits();
    }

    private OrderStatus statusOf(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private void sendSessionEvent(String type, String sessionId, String paymentStatus, long amount) throws Exception {
        String payload = sessionEventPayload(type, sessionId, paymentStatus, amount);
        paymentService.processWebhookEvent(payload, signatureHeader(payload, WEBHOOK_SECRET));
    }

    private static String sessionEventPayload(String type, String sessionId, String paymentStatus, long amount) {
        long now = System.currentTimeMillis() / 1000;
        return """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": %d,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "payment_status": "%s",
                      "amount_total": %d,
                      "currency": "%s",
                      "metadata": {}
                    }
                  }
                }
                """.formatted(UUID.randomUUID().toString().replace("-", ""), Stripe.API_VERSION, now,
                type, sessionId, paymentStatus, amount, PLAN.getCurrency());
    }

    /** Builds a Stripe-Signature header: t=<ts>,v1=HMAC_SHA256(secret, "<ts>.<payload>"). */
    private static String signatureHeader(String payload, String secret) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
        return "t=" + timestamp + ",v1=" + signature;
    }

    /** Starts all tasks at the same instant and returns their results in order. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
