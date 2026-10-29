package com.resumetailor.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Feeds password login outcomes into the per-email failure limit.
 * Unknown emails also arrive here as bad credentials, so they are throttled the same way.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

    private final RateLimiter limiter;

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        limiter.recordLoginFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken) {
            limiter.clearLoginFailures(event.getAuthentication().getName());
        }
    }
}
