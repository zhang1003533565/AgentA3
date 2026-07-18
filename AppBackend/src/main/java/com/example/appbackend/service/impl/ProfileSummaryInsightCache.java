package com.example.appbackend.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Small synchronized LRU cache for AI-generated profile insights.
 * Entries expire after the latest read or write and the eldest entry is evicted at capacity.
 */
final class ProfileSummaryInsightCache<V> {

    private final int maxEntries;
    private final Duration expiry;
    private final Clock clock;
    private final LinkedHashMap<Long, TimedValue<V>> entries =
            new LinkedHashMap<>(16, 0.75f, true);

    ProfileSummaryInsightCache(int maxEntries, Duration expiry, Clock clock) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        if (expiry == null || expiry.isZero() || expiry.isNegative()) {
            throw new IllegalArgumentException("expiry must be positive");
        }
        this.maxEntries = maxEntries;
        this.expiry = expiry;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized V get(Long userId) {
        Instant now = clock.instant();
        evictExpired(now);
        TimedValue<V> timed = entries.get(userId);
        if (timed == null) {
            return null;
        }
        timed.expiresAt = now.plus(expiry);
        return timed.value;
    }

    synchronized void put(Long userId, V value) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(value, "value");
        Instant now = clock.instant();
        evictExpired(now);
        entries.put(userId, new TimedValue<>(value, now.plus(expiry)));
        evictOverCapacity();
    }

    synchronized void remove(Long userId) {
        entries.remove(userId);
    }

    synchronized int size() {
        evictExpired(clock.instant());
        return entries.size();
    }

    private void evictExpired(Instant now) {
        Iterator<Map.Entry<Long, TimedValue<V>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            TimedValue<V> timed = iterator.next().getValue();
            if (timed.expiresAt.isAfter(now)) {
                break;
            }
            iterator.remove();
        }
    }

    private void evictOverCapacity() {
        Iterator<Map.Entry<Long, TimedValue<V>>> iterator = entries.entrySet().iterator();
        while (entries.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class TimedValue<V> {
        private final V value;
        private Instant expiresAt;

        private TimedValue(V value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
