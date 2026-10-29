package com.resumetailor.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the client IP used as a rate limit key.
 *
 * In production Apache proxies to the app on localhost, so every request's
 * remote address is the proxy. X-Forwarded-For is only trusted when the request
 * comes from a trusted proxy, and only its last entry is used: that is the one
 * the proxy appended, while earlier entries are whatever the client sent.
 */
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @Value("${app.rate-limit.trusted-proxies:127.0.0.1,0:0:0:0:0:0:0:1,::1}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) return remote;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;

        String[] hops = forwarded.split(",");
        String client = hops[hops.length - 1].trim();
        return client.isEmpty() ? remote : client;
    }
}
