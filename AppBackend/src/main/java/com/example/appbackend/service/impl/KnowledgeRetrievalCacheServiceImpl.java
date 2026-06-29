package com.example.appbackend.service.impl;

import com.example.appbackend.dto.KnowledgeChatDTO;
import com.example.appbackend.service.KnowledgeRetrievalCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

@Service
public class KnowledgeRetrievalCacheServiceImpl implements KnowledgeRetrievalCacheService {
    private static final long DEFAULT_TTL_SECONDS = 300L;
    private static final int DEFAULT_MAX_ENTRIES = 500;

    private final ConcurrentMap<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final LongAdder estimatedSavedMillis = new LongAdder();
    private final AtomicLong lastHitAt = new AtomicLong();
    private final AtomicLong lastMissAt = new AtomicLong();
    private final long ttlSeconds;
    private final int maxEntries;

    public KnowledgeRetrievalCacheServiceImpl(
            @Value("${knowledge.maxkb.retrieval-cache.ttl-seconds:300}") long ttlSeconds,
            @Value("${knowledge.maxkb.retrieval-cache.max-entries:500}") int maxEntries
    ) {
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        this.maxEntries = maxEntries > 0 ? maxEntries : DEFAULT_MAX_ENTRIES;
    }

    @Override
    public KnowledgeChatDTO.CacheLookupResult getOrLoad(
            Long accountId,
            String knowledgeId,
            String question,
            String searchMode,
            int topNumber,
            double similarity,
            Supplier<KnowledgeChatDTO.RetrievalPayload> loader
    ) {
        requestCount.increment();
        long now = System.currentTimeMillis();
        CacheKey key = CacheKey.from(accountId, knowledgeId, question, searchMode, topNumber, similarity);
        CacheEntry existing = entries.get(key);
        if (existing != null && !existing.isExpired(now)) {
            hitCount.increment();
            lastHitAt.set(now);
            existing.touch(now);
            estimatedSavedMillis.add(Math.max(0L, existing.payload().getRetrievalElapsedMs()));
            return buildLookupResult(true, key, existing.payload(), 0L, existing.expiresAt());
        }
        if (existing != null) {
            entries.remove(key, existing);
        }

        missCount.increment();
        lastMissAt.set(now);
        long startedAt = System.currentTimeMillis();
        KnowledgeChatDTO.RetrievalPayload payload = loader.get();
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - startedAt);
        if (payload.getRetrievalElapsedMs() == null || payload.getRetrievalElapsedMs() < 1) {
            payload.setRetrievalElapsedMs(elapsedMs);
        }
        long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        entries.put(key, new CacheEntry(payload, System.currentTimeMillis(), expiresAt, System.currentTimeMillis()));
        trimExpiredAndOverflow();
        return buildLookupResult(false, key, payload, elapsedMs, expiresAt);
    }

    @Override
    public KnowledgeChatDTO.CacheStats getStats() {
        trimExpiredAndOverflow();
        long requests = requestCount.sum();
        long hits = hitCount.sum();
        long misses = missCount.sum();
        KnowledgeChatDTO.CacheStats stats = new KnowledgeChatDTO.CacheStats();
        stats.setEnabled(true);
        stats.setRequestCount(requests);
        stats.setHitCount(hits);
        stats.setMissCount(misses);
        stats.setHitRate(requests == 0 ? 0D : (double) hits / requests);
        stats.setEntryCount(entries.size());
        stats.setMaxEntries(maxEntries);
        stats.setTtlSeconds(ttlSeconds);
        stats.setEstimatedSavedMillis(estimatedSavedMillis.sum());
        stats.setLastHitAt(toIso(lastHitAt.get()));
        stats.setLastMissAt(toIso(lastMissAt.get()));
        return stats;
    }

    @Override
    public void invalidateAccount(Long accountId) {
        if (accountId == null) {
            return;
        }
        entries.keySet().removeIf(key -> accountId.equals(key.accountId()));
    }

    @Override
    public void invalidateKnowledge(Long accountId, String knowledgeId) {
        if (accountId == null || knowledgeId == null || knowledgeId.isBlank()) {
            return;
        }
        String normalizedKnowledgeId = knowledgeId.trim();
        entries.keySet().removeIf(key -> accountId.equals(key.accountId()) && normalizedKnowledgeId.equals(key.knowledgeId()));
    }

    @Override
    public void clear() {
        entries.clear();
        requestCount.reset();
        hitCount.reset();
        missCount.reset();
        estimatedSavedMillis.reset();
        lastHitAt.set(0L);
        lastMissAt.set(0L);
    }

    private KnowledgeChatDTO.CacheLookupResult buildLookupResult(
            boolean hit,
            CacheKey key,
            KnowledgeChatDTO.RetrievalPayload payload,
            long lookupElapsedMs,
            long expiresAt
    ) {
        KnowledgeChatDTO.CacheLookupResult result = new KnowledgeChatDTO.CacheLookupResult();
        result.setCacheHit(hit);
        result.setCacheKey(key.hash());
        result.setPayload(payload);
        result.setLookupElapsedMs(lookupElapsedMs);
        result.setExpiresAt(toIso(expiresAt));
        return result;
    }

    private void trimExpiredAndOverflow() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        if (entries.size() <= maxEntries) {
            return;
        }

        List<CacheKey> oldest = new ArrayList<>(entries.keySet());
        oldest.sort(Comparator.comparingLong(key -> entries.get(key) == null ? Long.MAX_VALUE : entries.get(key).lastAccessAt()));
        int overflow = entries.size() - maxEntries;
        for (int index = 0; index < overflow && index < oldest.size(); index++) {
            entries.remove(oldest.get(index));
        }
    }

    private String toIso(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    private record CacheKey(
            Long accountId,
            String knowledgeId,
            String question,
            String searchMode,
            int topNumber,
            String similarity,
            String hash
    ) {
        private static CacheKey from(
                Long accountId,
                String knowledgeId,
                String question,
                String searchMode,
                int topNumber,
                double similarity
        ) {
            String normalizedKnowledgeId = knowledgeId == null ? "" : knowledgeId.trim();
            String normalizedQuestion = normalizeQuestion(question);
            String normalizedSearchMode = searchMode == null ? "" : searchMode.trim().toLowerCase(Locale.ROOT);
            String normalizedSimilarity = String.format(Locale.ROOT, "%.6f", similarity);
            String raw = accountId + "\n"
                    + normalizedKnowledgeId + "\n"
                    + normalizedQuestion + "\n"
                    + normalizedSearchMode + "\n"
                    + topNumber + "\n"
                    + normalizedSimilarity;
            return new CacheKey(
                    accountId,
                    normalizedKnowledgeId,
                    normalizedQuestion,
                    normalizedSearchMode,
                    topNumber,
                    normalizedSimilarity,
                    sha256(raw)
            );
        }

        private static String normalizeQuestion(String question) {
            if (question == null) {
                return "";
            }
            return question.trim().replaceAll("\\s+", " ");
        }

        private static String sha256(String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception error) {
                return Integer.toHexString(value.hashCode());
            }
        }
    }

    private record CacheEntry(
            KnowledgeChatDTO.RetrievalPayload payload,
            long createdAt,
            long expiresAt,
            AtomicLong lastAccessAtRef
    ) {
        private CacheEntry(KnowledgeChatDTO.RetrievalPayload payload, long createdAt, long expiresAt, long lastAccessAt) {
            this(payload, createdAt, expiresAt, new AtomicLong(lastAccessAt));
        }

        private boolean isExpired(long now) {
            return expiresAt <= now;
        }

        private void touch(long now) {
            lastAccessAtRef.set(now);
        }

        private long lastAccessAt() {
            return lastAccessAtRef.get();
        }
    }
}
