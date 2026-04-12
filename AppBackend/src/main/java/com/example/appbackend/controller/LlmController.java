package com.example.appbackend.controller;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Tag(name = "AI 对话", description = "基于 DeepSeek 的 AI 对话接口")
public class LlmController {

    private final LlmService llmService;

    public LlmController(LlmService llmService) {
        this.llmService = llmService;
    }

    @PostMapping({"/api/ai/chat", "/api/llm/chat"})
    @Operation(summary = "AI 对话", description = "传入 sessionId、prompt、input，返回模型回答和会话标识")
    public Result<LlmChatResponse> chat(@Valid @RequestBody LlmChatRequest request, HttpServletRequest httpRequest) {
        String authorization = httpRequest.getHeader("Authorization");
        return Result.success(llmService.chat(request, authorization));
    }
}
