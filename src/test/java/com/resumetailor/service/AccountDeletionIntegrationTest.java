package com.resumetailor.service;

import com.resumetailor.model.PasswordResetToken;
import com.resumetailor.model.ResumeHistory;
import com.resumetailor.model.User;
import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.payment.entity.Order;
import com.resumetailor.payment.entity.OrderStatus;
import com.resumetailor.payment.repository.OrderRepository;
import com.resumetailor.repository.PasswordResetTokenRepository;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.AccountService.DeletionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Account deletion against a real PostgreSQL, to check the ON DELETE CASCADE
 * foreign keys and that payment records survive. Runs only when IT_DB_URL is set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
class AccountDeletionIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("IT_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("IT_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("IT_DB_PASSWORD"));
        registry.add("stripe.secret-key", () -> "sk_test_integration");
        registry.add("stripe.webhook-secret", () -> "whsec_integration_test_secret");
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

    @Autowired private AccountService accountService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeHistoryRepository historyRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void deletesUserHistoryAndTokensButKeepsOrders() {
        User user = new User();
        user.setEmail("delete-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setProvider("LOCAL");
        user.setCredits(3);
        user = userRepository.save(user);

        ResumeHistory entry = new ResumeHistory();
        entry.setUser(user);
        entry.setTailoredText("Tailored resume");
        entry.setFormat("pdf");
        entry.setPdfFilename("resume-tailored-" + UUID.randomUUID() + ".pdf");
        entry = historyRepository.save(entry);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken("hash-" + UUID.randomUUID());
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token = tokenRepository.save(token);

        CreditPlan plan = CreditPlan.STARTER;
        Order order = orderRepository.save(Order.builder()
                .productName(plan.getProductName())
                .amount(plan.getAmount())
                .currency(plan.getCurrency())
                .status(OrderStatus.PAID)
                .userId(user.getId())
                .creditsToAdd(plan.getCreditsToAdd())
                .stripeSessionId("cs_test_" + UUID.randomUUID())
                .build());

        assertThat(accountService.deleteAccount(user.getEmail(), "wrong")).isEqualTo(DeletionResult.WRONG_PASSWORD);
        assertThat(userRepository.findById(user.getId())).isPresent();

        assertThat(accountService.deleteAccount(user.getEmail(), "secret123")).isEqualTo(DeletionResult.DELETED);

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(historyRepository.findById(entry.getId())).isEmpty();
        assertThat(tokenRepository.findById(token.getId())).isEmpty();
        assertThat(orderRepository.findById(order.getId())).isPresent();

        // A payment completing after the deletion has nobody to credit and must not fail.
        assertThat(userService.addCredits(user.getId(), 5)).isEmpty();
    }
}
