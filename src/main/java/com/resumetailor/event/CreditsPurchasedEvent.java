package com.resumetailor.event;

import com.resumetailor.model.User;

/**
 * Published after credits are successfully added to a user's account.
 * The user object carries the already-updated credit balance so listeners
 * can show the correct "new balance" without an extra database read.
 *
 * Listeners react via @TransactionalEventListener(AFTER_COMMIT) so this event
 * is never handled if the payment transaction rolls back — the user will never
 * receive a confirmation email for a credit grant that did not actually happen.
 */
public record CreditsPurchasedEvent(User user, int creditsPurchased) {}
