package com.resumetailor.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimiter limiter = new RateLimiter();
    private final RateLimitFilter filter =
            new RateLimitFilter(limiter, new ClientIpResolver("127.0.0.1"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(post("/login", "10.0.0.1", "user" + i + "@example.com").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = post("/login", "10.0.0.1", "other@example.com");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isPositive();

        assertThat(post("/login", "10.0.0.2", "other@example.com").getStatus()).isEqualTo(200);
    }

    @Test
    void emailIsLockedAfterFiveFailuresFromAnyIp() throws Exception {
        for (int i = 0; i < 5; i++) limiter.recordLoginFailure("victim@example.com");

        assertThat(post("/login", "10.0.0.9", " Victim@Example.com ").getStatus()).isEqualTo(429);
        assertThat(post("/login", "10.0.0.9", "someone-else@example.com").getStatus()).isEqualTo(200);
    }

    @Test
    void fewerFailuresOrASuccessfulLoginDoNotLock() throws Exception {
        for (int i = 0; i < 4; i++) limiter.recordLoginFailure("user@example.com");
        assertThat(post("/login", "10.0.0.1", "user@example.com").getStatus()).isEqualTo(200);

        limiter.recordLoginFailure("user@example.com");
        limiter.clearLoginFailures("user@example.com");
        assertThat(post("/login", "10.0.0.1", "user@example.com").getStatus()).isEqualTo(200);
    }

    @Test
    void registerIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) assertThat(post("/register", "10.0.0.1", null).getStatus()).isEqualTo(200);
        assertThat(post("/register", "10.0.0.1", null).getStatus()).isEqualTo(429);
    }

    @Test
    void forgotAndResetPasswordAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) post("/forgot-password", "10.0.0.1", null);
        assertThat(post("/forgot-password", "10.0.0.1", null).getStatus()).isEqualTo(429);

        for (int i = 0; i < 10; i++) post("/reset-password", "10.0.0.1", null);
        assertThat(post("/reset-password", "10.0.0.1", null).getStatus()).isEqualTo(429);
    }

    @Test
    void forwardedForIsOnlyTrustedFromTheProxyAndOnlyItsLastHop() throws Exception {
        // Behind the proxy: the client cannot dodge the limit by prepending fake hops.
        for (int i = 0; i < 5; i++) {
            postVia("/register", "127.0.0.1", "1.1.1." + i + ", 203.0.113.7");
        }
        assertThat(postVia("/register", "127.0.0.1", "9.9.9.9, 203.0.113.7").getStatus()).isEqualTo(429);
        assertThat(postVia("/register", "127.0.0.1", "203.0.113.8").getStatus()).isEqualTo(200);

        // Not from the proxy: the header is ignored and the socket address is the key.
        for (int i = 0; i < 5; i++) postVia("/register", "198.51.100.1", "203.0.113." + (100 + i));
        assertThat(postVia("/register", "198.51.100.1", "203.0.113.200").getStatus()).isEqualTo(429);
    }

    @Test
    void tailorAllowsOneGenerationAtATimePerUser() throws Exception {
        login("user@example.com");
        AtomicInteger nestedStatus = new AtomicInteger();

        // While the first request is still in the chain, a second one from the same user arrives.
        FilterChain slowChain = (req, res) -> {
            MockHttpServletResponse nested = new MockHttpServletResponse();
            filter.doFilter(request("/api/tailor", "10.0.0.1", null), nested, new MockFilterChain());
            nestedStatus.set(nested.getStatus());
        };
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/tailor", "10.0.0.1", null), first, slowChain);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(nestedStatus.get()).isEqualTo(429);
        // Once the first one finished, the user can generate again.
        assertThat(post("/tailor", "10.0.0.1", null).getStatus()).isEqualTo(200);
    }

    @Test
    void tailorIsLimitedPerUserNotPerIp() throws Exception {
        login("user@example.com");
        for (int i = 0; i < 5; i++) assertThat(post("/tailor", "10.0.0." + i, null).getStatus()).isEqualTo(200);
        assertThat(post("/api/tailor", "10.0.0.99", null).getStatus()).isEqualTo(429);

        login("other@example.com");
        assertThat(post("/tailor", "10.0.0.1", null).getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousTailorAndCheckoutPassThroughToSecurity() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(post("/tailor", "10.0.0.1", null).getStatus()).isEqualTo(200);
            assertThat(post("/api/payment/checkout", "10.0.0.1", null).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void checkoutIsLimitedPerUser() throws Exception {
        login("buyer@example.com");
        for (int i = 0; i < 5; i++) post("/api/payment/checkout", "10.0.0.1", null);
        assertThat(post("/api/payment/checkout", "10.0.0.1", null).getStatus()).isEqualTo(429);
    }

    @Test
    void accountDeletionIsLimitedPerUser() throws Exception {
        login("user@example.com");
        for (int i = 0; i < 5; i++) post("/account/delete", "10.0.0.1", null);
        assertThat(post("/account/delete", "10.0.0.1", null).getStatus()).isEqualTo(429);
    }

    @Test
    void getRequestsAndOtherPathsAreNotLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest get = request("/login", "10.0.0.1", null);
            get.setMethod("GET");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(get, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);

            assertThat(post("/api/webhook/stripe", "10.0.0.1", null).getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse post(String path, String ip, String email) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(path, ip, email), response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse postVia(String path, String proxyIp, String forwardedFor) throws Exception {
        MockHttpServletRequest request = request(path, proxyIp, null);
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String path, String ip, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        if (email != null) request.setParameter("email", email);
        return request;
    }

    private static void login(String user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, "ROLE_USER"));
    }
}
