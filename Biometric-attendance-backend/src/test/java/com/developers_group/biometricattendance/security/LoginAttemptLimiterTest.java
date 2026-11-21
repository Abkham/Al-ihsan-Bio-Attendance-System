package com.developers_group.biometricattendance.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {

    @Test
    void blocksAfterTooManyFailuresAndRecoversAfterBlockPeriod() {
        AtomicLong now = new AtomicLong(1_000);
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                3, Duration.ofMinutes(10), Duration.ofMinutes(15), now::get);

        assertFalse(limiter.isBlocked("amina"));
        limiter.recordFailure("amina");
        limiter.recordFailure("amina");
        assertFalse(limiter.isBlocked("amina"));
        limiter.recordFailure("amina");
        assertTrue(limiter.isBlocked("amina"));

        // another user is not affected
        assertFalse(limiter.isBlocked("fatma"));

        now.addAndGet(Duration.ofMinutes(16).toMillis());
        assertFalse(limiter.isBlocked("amina"));
    }

    @Test
    void successClearsTheCounter() {
        AtomicLong now = new AtomicLong(1_000);
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                3, Duration.ofMinutes(10), Duration.ofMinutes(15), now::get);

        limiter.recordFailure("amina");
        limiter.recordFailure("amina");
        limiter.recordSuccess("amina");
        limiter.recordFailure("amina");
        limiter.recordFailure("amina");

        assertFalse(limiter.isBlocked("amina"));
    }

    @Test
    void oldFailuresOutsideTheWindowAreForgotten() {
        AtomicLong now = new AtomicLong(1_000);
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                3, Duration.ofMinutes(10), Duration.ofMinutes(15), now::get);

        limiter.recordFailure("amina");
        limiter.recordFailure("amina");
        now.addAndGet(Duration.ofMinutes(11).toMillis());
        limiter.recordFailure("amina");

        assertFalse(limiter.isBlocked("amina"));
    }
}
