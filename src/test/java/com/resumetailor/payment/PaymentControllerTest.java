package com.resumetailor.payment;

import com.resumetailor.payment.controller.PaymentController;
import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.payment.exception.InvalidPlanException;
import com.resumetailor.payment.exception.PaymentException;
import com.resumetailor.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    @Test
    void someoneElsesOrMissingOrderRaisesA404ForTheErrorHandler() {
        PaymentService paymentService = mock(PaymentService.class);
        when(paymentService.findOrderForUser(9L, "user@example.com")).thenReturn(Optional.empty());
        PaymentController controller = new PaymentController(paymentService);

        assertThatThrownBy(() -> controller.getOrder(9L, new TestingAuthenticationToken("user@example.com", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unknownPlanIsAnInvalidPlanException() {
        assertThatThrownBy(() -> CreditPlan.fromId("GOLD"))
                .isInstanceOf(InvalidPlanException.class)
                .isInstanceOf(PaymentException.class);
        assertThat(CreditPlan.fromId("pro")).isEqualTo(CreditPlan.PRO);
    }
}
