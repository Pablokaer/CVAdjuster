package com.resumetailor.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.local.LocalBucketBuilder;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * A set of token buckets keyed by a string (client IP, email or user).
 *
 * Buckets live in memory, which is enough for the single-instance deployment.
 * Idle keys are evicted once their longest window has passed, and the cache is
 * capped so a flood of distinct keys cannot exhaust the heap.
 */
public final class KeyedLimit {

    private static final long MAX_KEYS = 100_000;

    private final Bandwidth[] limits;
    private final Cache<String, Bucket> buckets;

    public KeyedLimit(Bandwidth... limits) {
        this.limits = limits.clone();
        long longestWindowNanos = Arrays.stream(limits)
                .mapToLong(Bandwidth::getRefillPeriodNanos)
                .max()
                .orElseThrow();
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_KEYS)
                .expireAfterAccess(Duration.ofNanos(longestWindowNanos))
                .build();
    }

    /** Takes one token. Returns 0 when allowed, otherwise the seconds to wait. */
    public long tryConsume(String key) {
        ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? 0 : toRetrySeconds(probe.getNanosToWaitForRefill());
    }

    /** Checks for a token without taking it. Returns 0 when one is available. */
    public long secondsUntilAvailable(String key) {
        Bucket bucket = buckets.getIfPresent(key);
        if (bucket == null) return 0;
        long nanos = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
        return nanos == 0 ? 0 : toRetrySeconds(nanos);
    }

    public void reset(String key) {
        buckets.invalidate(key);
    }

    private Bucket newBucket() {
        LocalBucketBuilder builder = Bucket.builder();
        for (Bandwidth limit : limits) builder.addLimit(limit);
        return builder.build();
    }

    private static long toRetrySeconds(long nanos) {
        return TimeUnit.NANOSECONDS.toSeconds(nanos) + 1;
    }
}
