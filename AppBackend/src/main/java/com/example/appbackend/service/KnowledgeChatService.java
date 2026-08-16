package com.example.appbackend.service;

import com.example.appbackend.dto.KnowledgeChatDTO;

public interface KnowledgeChatService {

    KnowledgeChatDTO.RetrievalResult retrieve(KnowledgeChatDTO.RetrievalRequest request);

    KnowledgeChatDTO.ChatResponse chat(KnowledgeChatDTO.ChatRequest request, String authorization);

    KnowledgeChatDTO.CacheStats getCacheStats();

    void clearCache();
}
