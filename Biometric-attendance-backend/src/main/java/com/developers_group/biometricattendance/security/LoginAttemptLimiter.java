package com.developers_group.biometricattendance.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Small in-memory throttle for password guessing and reset-request abuse.
 *
 * After {@code maxFailures} failures inside {@code window} the key is blocked
 * until the window expires. It is per application instance (fine for a single
 * school server; use a shared store if you ever run several instances).
 */
public class LoginAttemptLimiter {

    private static final class Entry {
        int failures;
        long windowStart;
        long blockedUntil;
    }

    private final int maxFailures;
    private final long windowMillis;
    private final long blockMillis;
    private final LongSupplier nowMillis;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, Duration window, Duration block) {
        this(maxFailures, window, block, System::currentTimeMillis);
    }

    public LoginAttemptLimiter(int maxFailures, Duration window, Duration block, LongSupplier nowMillis) {
        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
        this.blockMillis = block.toMillis();
        this.nowMillis = nowMillis;
    }

    public boolean isBlocked(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return false;
        synchronized (entry) {
            return entry.blockedUntil > nowMillis.getAsLong();
        }
    }

    public void recordFailure(String key) {
        long now = nowMillis.getAsLong();
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        synchronized (entry) {
            if (entry.windowStart == 0 || now - entry.windowStart > windowMillis) {
                entry.windowStart = now;
                entry.failures = 0;
            }
            entry.failures++;
            if (entry.failures >= maxFailures) {
                entry.blockedUntil = now + blockMillis;
                entry.failures = 0;
                entry.windowStart = 0;
            }
        }
        if (entries.size() > 10_000) {
            entries.entrySet().removeIf(e -> e.getValue().blockedUntil < now
                    && now - e.getValue().windowStart > windowMillis);
        }
    }

    public void recordSuccess(String key) {
        entries.remove(key);
    }
}
