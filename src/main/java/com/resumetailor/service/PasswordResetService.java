package com.resumetailor.service;

import com.resumetailor.exception.InvalidTokenException;
import com.resumetailor.model.PasswordResetToken;
import com.resumetailor.model.User;
import com.resumetailor.repository.PasswordResetTokenRepository;
import com.resumetailor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_EXPIRY_HOURS   = 1;
    private static final int COOLDOWN_SECONDS     = 60;  // min gap between requests per user
    private static final int TOKEN_BYTES          = 32;  // 256-bit raw token

    // SecureRandom is thread-safe and expensive to construct — reuse one instance.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    // ── request reset ─────────────────────────────────────────────────────────

    /**
     * Issues a new reset token for the given email.
     *
     * Security properties:
     *  - Returns silently for unknown emails — callers always see the same response
     *    (prevents user enumeration).
     *  - Enforces a per-user cooldown to stop inbox flooding.
     *  - Stores only the SHA-256 hash; the raw token travels only via email.
     */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (isOnCooldown(user)) {
                // Do not log the email address — log the user ID only
                log.warn("SECURITY: reset request rate-limited, userId={}", user.getId());
                return;
            }

            // Invalidate any previous token before creating a fresh one
            tokenRepository.deleteByUser(user);

            String rawToken   = generateToken();
            String hashedToken = hashToken(rawToken);

            PasswordResetToken prt = new PasswordResetToken();
            prt.setToken(hashedToken);            // only the hash is persisted
            prt.setUser(user);
            prt.setCreatedAt(Instant.now());
            prt.setExpiresAt(Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS));
            tokenRepository.save(prt);

            // Raw token goes into the email and is never stored anywhere else.
            // @Async — dispatched on a background thread; email failure never
            // breaks this transaction or the caller's HTTP response.
            notificationService.sendPasswordResetEmail(user, rawToken);
            log.info("SECURITY: reset token issued, userId={}", user.getId());
        });
    }

    // ── validate ──────────────────────────────────────────────────────────────

    /**
     * Validates a raw token without consuming it.
     * Hashes the input before the database lookup so the raw value is
     * never compared directly against stored data.
     *
     * Both "not found" and "expired/used" raise the same exception —
     * the caller cannot distinguish between the two cases.
     */
    public PasswordResetToken validateToken(String rawToken) {
        String hashed = hashToken(rawToken);
        PasswordResetToken prt = tokenRepository.findByToken(hashed)
                .orElseThrow(InvalidTokenException::new);

        if (prt.isUsed() || prt.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException();
        }
        return prt;
    }

    // ── reset password ────────────────────────────────────────────────────────

    /**
     * Validates the token, updates the password, and marks the token as used —
     * all in a single atomic transaction. A rollback on any step leaves the
     * token unconsumed and the password unchanged.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken prt = validateToken(rawToken);

        User user = prt.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark as used inside the same transaction — prevents replay within the window
        prt.setUsed(true);
        tokenRepository.save(prt);
        log.info("SECURITY: password reset completed, userId={}", user.getId());
    }

    // ── housekeeping ──────────────────────────────────────────────────────────

    /** Prevents unbounded table growth. Runs at the top of every hour. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        tokenRepository.deleteByExpiresAtBefore(Instant.now());
        log.debug("Purged expired password reset tokens");
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Generates a 256-bit cryptographically random token, URL-safe Base64 encoded.
     * Using raw SecureRandom bytes rather than UUID because:
     *  - double the entropy (256 vs 122 bits)
     *  - no recognizable structure/format that reveals it is a UUID
     *  - produces a shorter, cleaner URL parameter (43 chars vs 36 with dashes)
     */
    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * One-way SHA-256 hash of the raw token for safe storage.
     * If the database is compromised, leaked hashes cannot be used directly —
     * an attacker would need to reverse SHA-256 with 256-bit pre-image resistance.
     */
    private static String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Returns true if the user has a live (unused) token created within the
     * cooldown window. Prevents inbox flooding and token-generation abuse.
     */
    private boolean isOnCooldown(User user) {
        Optional<PasswordResetToken> existing = tokenRepository.findByUserAndUsedFalse(user);
        return existing
                .map(t -> t.getCreatedAt().isAfter(Instant.now().minusSeconds(COOLDOWN_SECONDS)))
                .orElse(false);
    }
}
