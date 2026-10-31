package com.findit.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small in-memory fixed-window rate limiter.
 *
 * <p>It is not a replacement for a gateway/WAF, but it protects the login and
 * claim endpoints against trivial brute force and spam (see PRD, "Security").</p>
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @return {@code true} when the action is allowed, {@code false} when the
     *         caller exceeded the limit.
     */
    public boolean tryAcquire(String key, int maxAttempts, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAt >= windowSeconds) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt, existing.count + 1);
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= windowSeconds);
        }
        return window.count <= maxAttempts;
    }

    /** Removes the counter for the given key (used after a successful login). */
    public void reset(String key) {
        windows.remove(key);
    }

    private record Window(long startedAt, int count) {
    }
}
