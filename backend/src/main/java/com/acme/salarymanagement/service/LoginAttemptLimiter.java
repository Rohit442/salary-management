package com.acme.salarymanagement.service;

import com.acme.salarymanagement.util.EmailUtility;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class LoginAttemptLimiter {
    private record Key(String email, String client) { }
    private final Map<Key, ArrayDeque<Instant>> failures = new HashMap<>();
    private final Clock clock;
    private final int maximumFailures;
    private final Duration window;

    public LoginAttemptLimiter(Clock clock, int maximumFailures, Duration window) {
        if (maximumFailures < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Login attempt limits must be positive");
        }
        this.clock = clock;
        this.maximumFailures = maximumFailures;
        this.window = window;
    }

    public synchronized long retryAfterSeconds(String email, String client) {
        removeExpired();
        var attempts = failures.get(key(email, client));
        if (attempts == null || attempts.size() < maximumFailures) return 0;
        Duration remaining = Duration.between(clock.instant(), attempts.getFirst().plus(window));
        return remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
    }

    public synchronized void recordFailure(String email, String client) {
        removeExpired();
        var attempts = failures.computeIfAbsent(key(email, client), ignored -> new ArrayDeque<>());
        if (attempts.size() < maximumFailures) attempts.addLast(clock.instant());
    }

    public synchronized void recordSuccess(String email, String client) {
        failures.remove(key(email, client));
    }

    private Key key(String email, String client) {
        return new Key(EmailUtility.normalize(email), client);
    }

    private void removeExpired() {
        Instant cutoff = clock.instant().minus(window);
        failures.values().forEach(attempts -> {
            while (!attempts.isEmpty() && !attempts.getFirst().isAfter(cutoff)) attempts.removeFirst();
        });
        failures.values().removeIf(ArrayDeque::isEmpty);
    }
}
