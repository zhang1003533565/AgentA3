package com.example.appbackend.service;

import com.example.appbackend.dto.KnowledgeChatDTO;

import java.util.function.Supplier;

public interface KnowledgeRetrievalCacheService {

    KnowledgeChatDTO.CacheLookupResult getOrLoad(
            Long accountId,
            String knowledgeId,
            String question,
            String searchMode,
            int topNumber,
            double similarity,
            Supplier<KnowledgeChatDTO.RetrievalPayload> loader
    );

    KnowledgeChatDTO.CacheStats getStats();

    void invalidateAccount(Long accountId);

    void invalidateKnowledge(Long accountId, String knowledgeId);

    void clear();
}
