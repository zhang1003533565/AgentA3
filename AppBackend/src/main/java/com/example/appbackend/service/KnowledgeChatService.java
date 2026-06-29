package com.example.appbackend.service;

import com.example.appbackend.dto.KnowledgeChatDTO;

public interface KnowledgeChatService {

    KnowledgeChatDTO.ChatResponse chat(KnowledgeChatDTO.ChatRequest request, String authorization);
}
