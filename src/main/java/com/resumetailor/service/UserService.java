package com.resumetailor.service;

import com.resumetailor.dto.RegisterDTO;
import com.resumetailor.event.UserRegisteredEvent;
import java.util.Optional;
import com.resumetailor.exception.EmailAlreadyRegisteredException;
import com.resumetailor.exception.InsufficientCreditsException;
import com.resumetailor.model.User;
import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    // ── registration ──────────────────────────────────────────────────────────

    /**
     * Validates, persists, and returns a new LOCAL user.
     * Throws EmailAlreadyRegisteredException before touching the database
     * so the caller can redirect to the form without a constraint violation.
     *
     * The welcome email must be sent by the caller AFTER this method returns,
     * ensuring the transaction has committed before any notification is dispatched.
     */
    @Transactional
    public User register(RegisterDTO dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new EmailAlreadyRegisteredException(dto.getEmail());
        }
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setProvider("LOCAL");
        user.setCredits(0);
        User saved = userRepository.save(user);
        log.info("Registered new local user: {}", saved.getEmail());
        // Published inside @Transactional — the AFTER_COMMIT listener fires only
        // when this transaction actually commits, so the welcome email is never
        // sent for a registration that was rolled back.
        eventPublisher.publishEvent(new UserRegisteredEvent(saved));
        return saved;
    }

    /**
     * Extrai o email do usuário independente do tipo de autenticação.
     * Form login  → authentication.getName() já é o email.
     * Google OAuth2 → getName() retorna o subject ID; o email fica nos atributos.
     */
    public static String extractEmail(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauth2) {
            return (String) oauth2.getPrincipal().getAttributes().get("email");
        }
        return authentication.getName();
    }

    /**
     * Finds a user by email, or creates a new one if they don't exist yet.
     * This handles OAuth2 users who may not have been persisted before.
     */
    @Transactional
    public User getOrCreateByEmail(String email) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            log.info("Creating new user record for OAuth2 user: {}", email);
            User user = new User();
            user.setEmail(email);
            user.setProvider("GOOGLE");
            user.setEmailVerified(true);
            user.setCredits(0);
            return userRepository.save(user);
        });
    }

    /** Returns the credit balance for the given email, creating the record if absent. */
    @Transactional(readOnly = true)
    public int getCredits(String email) {
        return userRepository.findByEmail(email)
                .map(User::getCredits)
                .orElse(0);
    }

    /**
     * Atomically adds the given number of credits to the user.
     * Returns the updated User so the caller can act on the new balance
     * (e.g. send a purchase confirmation email) without a second DB round-trip.
     * Returns empty if no user with the given ID exists.
     */
    @Transactional
    public Optional<User> addCredits(Long userId, int amount) {
        return userRepository.findById(userId).map(user -> {
            user.setCredits(user.getCredits() + amount);
            User saved = userRepository.save(user);
            log.info("Added {} credits to userId={} (new balance: {})", amount, userId, saved.getCredits());
            return saved;
        });
    }

    /**
     * Deducts CreditPlan.CREDITS_PER_RESUME credits atomically.
     * Throws InsufficientCreditsException if the balance is insufficient.
     * Call this BEFORE starting AI processing.
     */
    @Transactional
    public void deductCredit(String email) {
        int cost = CreditPlan.CREDITS_PER_RESUME;
        User user = userRepository.findByEmail(email)
                .orElseThrow(InsufficientCreditsException::new);
        if (user.getCredits() < cost) {
            throw new InsufficientCreditsException();
        }
        user.setCredits(user.getCredits() - cost);
        userRepository.save(user);
        log.info("Deducted {} credit(s) from {} (new balance: {})", cost, email, user.getCredits());
    }

    /**
     * Refunds CreditPlan.CREDITS_PER_RESUME credits.
     * Called when AI processing fails after credits were already deducted.
     */
    @Transactional
    public void refundCredit(String email) {
        int cost = CreditPlan.CREDITS_PER_RESUME;
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setCredits(user.getCredits() + cost);
            userRepository.save(user);
            log.info("Refunded {} credit(s) to {} after processing failure (balance: {})", cost, email, user.getCredits());
        });
    }
}
