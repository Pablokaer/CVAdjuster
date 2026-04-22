package com.resumetailor.event;

import com.resumetailor.model.User;

/**
 * Published by UserService after a new local user is successfully persisted.
 * Listeners react via @TransactionalEventListener(AFTER_COMMIT) so they only
 * fire when the user row is genuinely durable in the database.
 */
public record UserRegisteredEvent(User user) {}
