package com.example.appbackend.service;

import java.util.List;
import java.util.Map;

public interface LlmMemoryService {

    String getOrCreateSessionId(String token, String requestedSessionId);

    String resolveSessionToken(String token, String sessionId);

    List<Map<String, String>> getHistoryMessages(String sessionToken);

    void appendConversation(String sessionToken, String userInput, String assistantAnswer);
}
