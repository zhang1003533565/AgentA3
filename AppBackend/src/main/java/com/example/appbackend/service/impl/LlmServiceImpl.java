package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.service.LlmService;
import org.springframework.stereotype.Service;

@Service
public class LlmServiceImpl implements LlmService {

    private final LangGraphAiService langGraphAiService;

    public LlmServiceImpl(LangGraphAiService langGraphAiService) {
        this.langGraphAiService = langGraphAiService;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request, String token) {
        return langGraphAiService.chat(request, token);
    }
}
