package com.resumetailor.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies {@link RateLimiter} to the POST endpoints that can be abused.
 *
 * Runs inside the Spring Security chain, after the session's user is loaded and
 * before form login is processed, so it can key by user and also guard login.
 * Rejected requests get a 429 with Retry-After, rendered by error/429.html for
 * browsers and as JSON for API clients.
 *
 * Not a @Component on purpose: Spring Boot would also register it as a plain
 * servlet filter and every request would be counted twice.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final ClientIpResolver ipResolver;

    public RateLimitFilter(RateLimiter limiter, ClientIpResolver ipResolver) {
        this.limiter = limiter;
        this.ipResolver = ipResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        switch (path) {
            case "/login" -> {
                if (allowLogin(request, response)) chain.doFilter(request, response);
            }
            case "/register" -> {
                if (allow(limiter.registerPerIp, ipResolver.resolve(request), path, response))
                    chain.doFilter(request, response);
            }
            case "/forgot-password" -> {
                if (allow(limiter.forgotPasswordPerIp, ipResolver.resolve(request), path, response))
                    chain.doFilter(request, response);
            }
            case "/reset-password" -> {
                if (allow(limiter.resetPasswordPerIp, ipResolver.resolve(request), path, response))
                    chain.doFilter(request, response);
            }
            case "/tailor", "/api/tailor" -> tailor(request, response, chain, path);
            case "/api/payment/checkout" -> {
                String user = currentUser();
                if (user == null || allow(limiter.checkoutPerUser, user, path, response))
                    chain.doFilter(request, response);
            }
            default -> chain.doFilter(request, response);
        }
    }

    private boolean allowLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!allow(limiter.loginPerIp, ipResolver.resolve(request), "/login", response)) return false;

        long lockout = limiter.loginLockoutSeconds(request.getParameter("email"));
        if (lockout > 0) {
            reject(response, lockout, "/login (too many failed attempts for this email)");
            return false;
        }
        return true;
    }

    /** One generation at a time per user, then the per-user rate. */
    private void tailor(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String path)
            throws ServletException, IOException {
        String user = currentUser();
        if (user == null) {
            // Not logged in: Spring Security redirects to /login further down the chain.
            chain.doFilter(request, response);
            return;
        }
        if (!limiter.startTailoring(user)) {
            reject(response, 30, path + " (generation already in progress)");
            return;
        }
        try {
            if (allow(limiter.tailorPerUser, user, path, response)) chain.doFilter(request, response);
        } finally {
            limiter.finishTailoring(user);
        }
    }

    private boolean allow(KeyedLimit limit, String key, String path, HttpServletResponse response) throws IOException {
        long retryAfter = limit.tryConsume(key);
        if (retryAfter == 0) return true;
        reject(response, retryAfter, path);
        return false;
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds, String what) throws IOException {
        // No IP or email in the log line, same as the rest of the security logging.
        log.warn("SECURITY: rate limit hit on {}, retry after {}s", what, retryAfterSeconds);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests");
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return null;
        return auth.getName();
    }
}
