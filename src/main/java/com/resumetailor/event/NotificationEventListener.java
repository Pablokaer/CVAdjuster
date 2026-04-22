package com.resumetailor.event;

import com.resumetailor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to domain events and dispatches email notifications.
 *
 * All methods use AFTER_COMMIT so emails are only ever sent when the
 * triggering transaction has successfully committed. If the transaction
 * rolls back (e.g. a constraint violation, a DB timeout) these listeners
 * are silently skipped — no email is sent for work that did not persist.
 *
 * The actual sending is delegated to NotificationService, whose methods
 * are @Async — the listener returns immediately and the email is dispatched
 * on the emailTaskExecutor thread pool.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        log.debug("UserRegisteredEvent received for userId={}", event.user().getId());
        notificationService.sendWelcomeEmail(event.user());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreditsPurchased(CreditsPurchasedEvent event) {
        log.debug("CreditsPurchasedEvent received for userId={}, credits={}",
                event.user().getId(), event.creditsPurchased());
        notificationService.sendPurchaseConfirmation(event.user(), event.creditsPurchased());
    }
}
