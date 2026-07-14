package com.example.appbackend.controller;

import com.example.appbackend.dto.AssistantResourceInteractionRequest;
import com.example.appbackend.dto.AssistantResourceInteractionResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.impl.AssistantResourceInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/leader")
public class AppAiLeaderResourceController {

    private final AssistantResourceInteractionService interactionService;

    public AppAiLeaderResourceController(AssistantResourceInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/sessions/{sessionId}/messages/{messageId}/resources/{resourceId}/interactions")
    @Operation(summary = "记录 App Leader 资源互动")
    public Result<AssistantResourceInteractionResponse> interact(
            @PathVariable String sessionId,
            @PathVariable Long messageId,
            @PathVariable String resourceId,
            @Valid @RequestBody AssistantResourceInteractionRequest request,
            HttpServletRequest httpRequest) {
        return Result.success(interactionService.record(
                currentUserId(httpRequest), sessionId, messageId, resourceId, request));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (!(userId instanceof Number number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return number.longValue();
    }
}
