package com.example.appbackend.service;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LlmService {

    LlmChatResponse chat(LlmChatRequest request, String token);

    SseEmitter streamChat(LlmChatRequest request, String token);
}
