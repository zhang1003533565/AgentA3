package com.example.appbackend.service.impl;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileSummaryInsightCacheTest {

    @Test
    void entryExpiresSixHoursAfterItsLatestAccessOrWrite() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-18T00:00:00Z"));
        ProfileSummaryInsightCache<String> cache =
                new ProfileSummaryInsightCache<>(1_000, Duration.ofHours(6), clock);

        cache.put(42L, "first");
        clock.advance(Duration.ofHours(5));
        assertThat(cache.get(42L)).isEqualTo("first");

        clock.advance(Duration.ofHours(5));
        assertThat(cache.get(42L)).isEqualTo("first");

        cache.put(42L, "rewritten");
        clock.advance(Duration.ofHours(6));
        assertThat(cache.get(42L)).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    void capacityEvictsTheLeastRecentlyAccessedEntry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-18T00:00:00Z"));
        ProfileSummaryInsightCache<String> cache =
                new ProfileSummaryInsightCache<>(2, Duration.ofHours(6), clock);

        cache.put(1L, "one");
        clock.advance(Duration.ofMinutes(1));
        cache.put(2L, "two");
        assertThat(cache.get(1L)).isEqualTo("one");

        clock.advance(Duration.ofMinutes(1));
        cache.put(3L, "three");

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get(1L)).isEqualTo("one");
        assertThat(cache.get(2L)).isNull();
        assertThat(cache.get(3L)).isEqualTo("three");
    }

    @Test
    void concurrentReadsAndWritesRemainBounded() throws Exception {
        ProfileSummaryInsightCache<String> cache = new ProfileSummaryInsightCache<>(
                1_000,
                Duration.ofHours(6),
                Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (long userId = 0; userId < 4_000; userId++) {
                long key = userId;
                tasks.add(() -> {
                    cache.put(key, "profile-" + key);
                    cache.get(key);
                    return null;
                });
            }
            executor.invokeAll(tasks).forEach(future -> {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
        } finally {
            executor.shutdownNow();
        }

        assertThat(cache.size()).isLessThanOrEqualTo(1_000);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
