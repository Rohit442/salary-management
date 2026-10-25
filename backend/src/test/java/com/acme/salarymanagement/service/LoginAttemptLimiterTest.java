package com.acme.salarymanagement.service;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {
    private final TestClock clock = new TestClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, Duration.ofMinutes(15));

    @Test
    void blocksAfterFiveFailuresAndExpiresAtTheRollingWindowBoundary() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(limiter.retryAfterSeconds("maya@acme.example", "client")).isZero();
            limiter.recordFailure(" MAYA@ACME.EXAMPLE ", "client");
        }
        assertThat(limiter.retryAfterSeconds("maya@acme.example", "client")).isEqualTo(900);
        clock.now = clock.now.plusMillis(899001);
        assertThat(limiter.retryAfterSeconds("maya@acme.example", "client")).isEqualTo(1);
        clock.now = clock.now.plusMillis(999);
        assertThat(limiter.retryAfterSeconds("maya@acme.example", "client")).isZero();
    }

    @Test
    void successClearsOnlyItsOwnEmailAndClientPair() {
        for (int attempt = 0; attempt < 5; attempt++) limiter.recordFailure("maya@acme.example", "one");
        assertThat(limiter.retryAfterSeconds("maya@acme.example", "two")).isZero();
        assertThat(limiter.retryAfterSeconds("other@acme.example", "one")).isZero();
        limiter.recordSuccess(" MAYA@ACME.EXAMPLE ", "one");
        assertThat(limiter.retryAfterSeconds("maya@acme.example", "one")).isZero();
    }

    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
