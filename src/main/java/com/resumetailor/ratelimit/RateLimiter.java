package com.resumetailor.ratelimit;

import io.github.bucket4j.Bandwidth;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every rate limit in the app, in one place.
 *
 * Anonymous endpoints are keyed by client IP. Login is also keyed by the email
 * being tried, counting failures only, so an attacker cannot lock a real user
 * out just by trying their address. Endpoints that cost money or CPU are keyed
 * by the authenticated user.
 */
@Component
public class RateLimiter {

    /** All login attempts from one IP, successful or not. */
    final KeyedLimit loginPerIp = new KeyedLimit(
            perWindow(20, Duration.ofMinutes(5)));

    /** Failed logins for one email; cleared on a successful login. */
    final KeyedLimit loginFailuresPerEmail = new KeyedLimit(
            Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(15)).build());

    /** Sign-ups from one IP. Each new account gets a free credit, which costs an OpenAI call. */
    final KeyedLimit registerPerIp = new KeyedLimit(
            perWindow(5, Duration.ofHours(1)),
            perWindow(20, Duration.ofDays(1)));

    /** Reset emails requested from one IP, on top of the per-user cooldown in PasswordResetService. */
    final KeyedLimit forgotPasswordPerIp = new KeyedLimit(
            perWindow(5, Duration.ofMinutes(15)),
            perWindow(20, Duration.ofDays(1)));

    /** New password submissions from one IP. */
    final KeyedLimit resetPasswordPerIp = new KeyedLimit(
            perWindow(10, Duration.ofMinutes(15)));

    /** Resume generations per user; each one runs a PDF parse and an OpenAI call. */
    final KeyedLimit tailorPerUser = new KeyedLimit(
            perWindow(5, Duration.ofMinutes(1)),
            perWindow(30, Duration.ofHours(1)));

    /** Stripe checkout sessions per user; each one creates an order row. */
    final KeyedLimit checkoutPerUser = new KeyedLimit(
            perWindow(5, Duration.ofMinutes(1)),
            perWindow(20, Duration.ofHours(1)));

    /** Users with a resume generation in progress; one at a time each. */
    private final Set<String> tailoringInProgress = ConcurrentHashMap.newKeySet();

    public void recordLoginFailure(String email) {
        if (email != null) loginFailuresPerEmail.tryConsume(normalizeEmail(email));
    }

    public void clearLoginFailures(String email) {
        if (email != null) loginFailuresPerEmail.reset(normalizeEmail(email));
    }

    /** Seconds until this email may try to log in again; 0 when it may now. */
    long loginLockoutSeconds(String email) {
        return email == null ? 0 : loginFailuresPerEmail.secondsUntilAvailable(normalizeEmail(email));
    }

    boolean startTailoring(String user) {
        return tailoringInProgress.add(user);
    }

    void finishTailoring(String user) {
        tailoringInProgress.remove(user);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Bandwidth perWindow(long requests, Duration window) {
        return Bandwidth.builder().capacity(requests).refillGreedy(requests, window).build();
    }
}
