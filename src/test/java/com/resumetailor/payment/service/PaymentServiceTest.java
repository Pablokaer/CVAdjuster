package com.resumetailor.payment.service;

import com.resumetailor.event.CreditsPurchasedEvent;
import com.resumetailor.model.User;
import com.resumetailor.payment.config.StripeConfig;
import com.resumetailor.payment.dto.SessionFacts;
import com.resumetailor.payment.entity.Order;
import com.resumetailor.payment.entity.OrderStatus;
import com.resumetailor.payment.repository.OrderRepository;
import com.resumetailor.payment.repository.StripeProcessedEventRepository;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final long USER_ID = 7L;
    private static final String SESSION_ID = "cs_test_123";

    @Mock private OrderRepository orderRepository;
    @Mock private StripeProcessedEventRepository processedEventRepository;
    @Mock private StripeConfig stripeConfig;
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PaymentService paymentService;

    // ── fulfil() ──────────────────────────────────────────────────────────────

    @Test
    void creditsOnceForPaidSessionWithMatchingAmount() {
        Order order = pendingOrder();
        when(userService.addCredits(USER_ID, 2)).thenReturn(Optional.of(user(USER_ID)));

        boolean result = paymentService.fulfil(order, facts("paid", 199L, "eur"));

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
        verify(userService, times(1)).addCredits(USER_ID, 2);
        verify(eventPublisher).publishEvent(any(CreditsPurchasedEvent.class));
    }

    @Test
    void acceptsUppercaseCurrencyFromStripe() {
        Order order = pendingOrder();
        when(userService.addCredits(USER_ID, 2)).thenReturn(Optional.of(user(USER_ID)));

        assertThat(paymentService.fulfil(order, facts("paid", 199L, "EUR"))).isTrue();

        verify(userService).addCredits(USER_ID, 2);
    }

    @Test
    void doesNotCreditUnpaidSession() {
        Order order = pendingOrder();

        assertThat(paymentService.fulfil(order, facts("unpaid", 199L, "eur"))).isFalse();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoCredit();
    }

    @Test
    void doesNotCreditWrongAmount() {
        Order order = pendingOrder();

        assertThat(paymentService.fulfil(order, facts("paid", 1L, "eur"))).isFalse();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoCredit();
    }

    @Test
    void doesNotCreditNullAmount() {
        Order order = pendingOrder();

        assertThat(paymentService.fulfil(order, facts("paid", null, "eur"))).isFalse();

        verifyNoCredit();
    }

    @Test
    void doesNotCreditWrongCurrency() {
        Order order = pendingOrder();

        assertThat(paymentService.fulfil(order, facts("paid", 199L, "usd"))).isFalse();

        verifyNoCredit();
    }

    @Test
    void doesNotCreditAlreadyPaidOrderAgain() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);

        assertThat(paymentService.fulfil(order, facts("paid", 199L, "eur"))).isTrue();

        verifyNoCredit();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"FAILED", "CANCELED"})
    void neverCreditsClosedOrders(OrderStatus status) {
        Order order = pendingOrder();
        order.setStatus(status);

        assertThat(paymentService.fulfil(order, facts("paid", 199L, "eur"))).isFalse();

        assertThat(order.getStatus()).isEqualTo(status);
        verifyNoCredit();
    }

    // ── SessionFacts ──────────────────────────────────────────────────────────

    @Test
    void sessionFactsParsesWebhookJson() {
        String json = """
                {
                  "id": "cs_test_abc",
                  "object": "checkout.session",
                  "payment_status": "paid",
                  "amount_total": 599,
                  "currency": "eur",
                  "metadata": { "order_id": "12", "user_id": "7", "credits": "8", "plan_id": "PRO" }
                }
                """;

        SessionFacts facts = SessionFacts.fromJson(json);

        assertThat(facts.sessionId()).isEqualTo("cs_test_abc");
        assertThat(facts.paymentStatus()).isEqualTo("paid");
        assertThat(facts.isPaid()).isTrue();
        assertThat(facts.amountTotal()).isEqualTo(599L);
        assertThat(facts.currency()).isEqualTo("eur");
        assertThat(facts.metadata())
                .containsEntry("order_id", "12")
                .containsEntry("user_id", "7")
                .containsEntry("credits", "8")
                .containsEntry("plan_id", "PRO");
    }

    @Test
    void sessionFactsToleratesMissingAmountAndMetadata() {
        SessionFacts facts = SessionFacts.fromJson("{\"id\":\"cs_1\",\"payment_status\":\"unpaid\"}");

        assertThat(facts.amountTotal()).isNull();
        assertThat(facts.metadata()).isEmpty();
        assertThat(facts.isPaid()).isFalse();
    }

    // ── findOrderForUser() ────────────────────────────────────────────────────

    @Test
    void findOrderForUserReturnsOwnOrder() {
        Order order = pendingOrder();
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user(USER_ID)));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThat(paymentService.findOrderForUser(1L, "owner@example.com")).contains(order);
    }

    @Test
    void findOrderForUserHidesOtherUsersOrders() {
        when(userRepository.findByEmail("intruder@example.com")).thenReturn(Optional.of(user(99L)));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder()));

        assertThat(paymentService.findOrderForUser(1L, "intruder@example.com")).isEmpty();
    }

    @Test
    void findOrderForUserIsEmptyForUnknownUser() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThat(paymentService.findOrderForUser(1L, "ghost@example.com")).isEmpty();
        verify(orderRepository, never()).findById(anyLong());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void verifyNoCredit() {
        verify(userService, never()).addCredits(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private static Order pendingOrder() {
        return Order.builder()
                .id(1L)
                .productName("Starter – 2 Credits")
                .amount(199L)
                .currency("eur")
                .status(OrderStatus.PENDING)
                .stripeSessionId(SESSION_ID)
                .userId(USER_ID)
                .creditsToAdd(2)
                .build();
    }

    private static SessionFacts facts(String paymentStatus, Long amount, String currency) {
        return new SessionFacts(SESSION_ID, paymentStatus, amount, currency, Map.of());
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        return user;
    }
}
