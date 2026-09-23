package com.kabus.tracking.support;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-memory token bucket rate limiter.
 *
 * <p>Sufficient for a single-node local deployment and a single Spring Boot
 * instance in production. For multi-instance clusters move this to a shared
 * store (Redis) - see docs/SCALABILITY.md. Buckets are evicted when idle to
 * avoid unbounded memory growth.</p>
 */
@Service
public class RateLimitService {

    private static final long BUCKET_IDLE_MS = 3600_000L;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @return {@code true} if the request may proceed (a token was consumed),
     *         {@code false} when the caller exceeded the limit.
     */
    public boolean allow(String key, int capacity, int tokensPerMinute) {
        if (tokensPerMinute <= 0) {
            return false;
        }
        return buckets.compute(key, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.lastRefillMs > BUCKET_IDLE_MS) {
                return new Bucket(capacity, tokensPerMinute, now);
            }
            existing.refill(now);
            return existing;
        }).tryConsume();
    }

    public long remaining(String key, int capacity) {
        Bucket b = buckets.get(key);
        return b == null ? capacity : (long) Math.max(0.0, b.tokens);
    }

    public void reset(String key) {
        buckets.remove(key);
    }

    private static final class Bucket {
        private final int capacity;
        private final int tokensPerMinute;
        private double tokens;
        private long lastRefillMs;

        Bucket(int capacity, int tokensPerMinute, long now) {
            this.capacity = capacity;
            this.tokensPerMinute = tokensPerMinute;
            this.tokens = capacity;
            this.lastRefillMs = now;
        }

        synchronized void refill(long now) {
            double elapsedMin = (now - lastRefillMs) / 60_000.0;
            tokens = Math.min(capacity, tokens + elapsedMin * tokensPerMinute);
            lastRefillMs = now;
        }

        synchronized boolean tryConsume() {
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    @SuppressWarnings("unused")
    private static final AtomicInteger UNUSED_COUNTER = new AtomicInteger();
}