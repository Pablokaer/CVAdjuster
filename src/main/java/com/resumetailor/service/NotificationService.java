package com.resumetailor.service;

import com.resumetailor.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    // ── public API ────────────────────────────────────────────────────────────

    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(User user) {
        log.debug("Queuing welcome email → {}", user.getEmail());
        emailService.sendHtml(
                user.getEmail(),
                "Welcome to Resume Tailor!",
                EmailTemplates.welcome(displayName(user))
        );
    }

    /**
     * @param token  a short-lived, single-use token stored in the database.
     *               The full reset URL is assembled here so callers never
     *               build URLs themselves.
     */
    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(User user, String token) {
        String resetLink = baseUrl + "/reset-password?token=" + token;
        log.debug("Queuing password-reset email → {}", user.getEmail());
        emailService.sendHtml(
                user.getEmail(),
                "Reset your Resume Tailor password",
                EmailTemplates.passwordReset(displayName(user), resetLink)
        );
    }

    /**
     * @param creditsPurchased  the credits added in this transaction.
     *                          user.getCredits() must already reflect the new balance.
     */
    @Async("emailTaskExecutor")
    public void sendPurchaseConfirmation(User user, int creditsPurchased) {
        log.debug("Queuing purchase confirmation → {}", user.getEmail());
        emailService.sendHtml(
                user.getEmail(),
                "Your purchase is confirmed — Resume Tailor",
                EmailTemplates.purchaseConfirmation(
                        displayName(user),
                        creditsPurchased,
                        user.getCredits()
                )
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Derives a friendly display name from the email address until the User
     * model gains a dedicated name field.
     */
    private static String displayName(User user) {
        String email = user.getEmail();
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
