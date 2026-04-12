package com.example.appbackend.service;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;

public interface LlmService {

    LlmChatResponse chat(LlmChatRequest request, String token);
}
