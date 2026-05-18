package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.service.LlmService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LlmServiceImpl implements LlmService {

    private final PythonAiProxyService pythonAiProxyService;

    public LlmServiceImpl(PythonAiProxyService pythonAiProxyService) {
        this.pythonAiProxyService = pythonAiProxyService;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request, String token) {
        return pythonAiProxyService.chat(request, token);
    }

    @Override
    public SseEmitter streamChat(LlmChatRequest request, String token) {
        return pythonAiProxyService.streamChat(request, token);
    }
}
