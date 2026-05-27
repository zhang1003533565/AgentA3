package com.example.appbackend.controller;

import com.example.appbackend.dto.AiLeaderMessageItem;
import com.example.appbackend.dto.AiLeaderSessionDetail;
import com.example.appbackend.dto.AiLeaderSessionItem;
import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.service.impl.PythonAiProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai/leader")
@Tag(name = "App Leader 智能助手", description = "App 端固定接入 Leader 智能体")
public class AppAiLeaderController {

    private static final String LEADER_AGENT = "leader_agent";

    private final PythonAiProxyService pythonAiProxyService;
    private final AiLeaderSessionRepository sessionRepository;
    private final AiLeaderMessageRepository messageRepository;

    public AppAiLeaderController(PythonAiProxyService pythonAiProxyService,
                                 AiLeaderSessionRepository sessionRepository,
                                 AiLeaderMessageRepository messageRepository) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @PostMapping("/query")
    @Operation(summary = "App 智能助手查询", description = "固定调用 Leader 智能体，不允许 App 端切换其它智能体")
    public Result<LlmChatResponse> query(@Valid @RequestBody LlmChatRequest request, HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        AiLeaderSession session = getOrCreateSession(userId, request.getSessionId(), request.getInput());
        saveMessage(session, AiLeaderMessage.ROLE_USER, request.getInput(), "text");
        refreshSession(session, request.getInput());

        Map<String, Object> payload = new HashMap<>();
        payload.put("input", request.getInput());
        payload.put("agentName", LEADER_AGENT);
        if (StringUtils.hasText(request.getRagStrategy())) {
            payload.put("ragStrategy", request.getRagStrategy().trim());
        }
        if (StringUtils.hasText(request.getLlmModel())) {
            payload.put("llmModel", request.getLlmModel().trim());
        }
        payload.put("metadata", Map.of(
                "source", "app_ai_assistant",
                "sessionId", request.getSessionId() == null ? "" : request.getSessionId()
        ));

        Object ragResult = pythonAiProxyService.queryRag(payload, httpRequest.getHeader("Authorization"));
        LlmChatResponse response = toChatResponse(session, ragResult);
        saveMessage(session, AiLeaderMessage.ROLE_ASSISTANT, response.getAnswer(), response.getAnswerType());
        refreshSession(session, response.getAnswer());
        return Result.success(response);
    }

    @GetMapping("/sessions")
    @Operation(summary = "App Leader 会话列表")
    public Result<PageResponse<AiLeaderSessionItem>> sessions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        Page<AiLeaderSession> page = sessionRepository.searchByUserId(
                userId, normalizedKeyword, PageRequest.of(safePage - 1, safeSize));
        List<AiLeaderSessionItem> records = page.getContent().stream()
                .map(this::toSessionItem)
                .collect(Collectors.toList());
        return Result.success(new PageResponse<>(records, page.getTotalElements(), safePage, safeSize));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "App Leader 会话详情")
    public Result<AiLeaderSessionDetail> sessionDetail(@PathVariable String sessionId, HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        AiLeaderSession session = sessionRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        AiLeaderSessionDetail detail = new AiLeaderSessionDetail();
        detail.setSession(toSessionItem(session));
        detail.setMessages(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(session.getId()).stream()
                .map(this::toMessageItem)
                .collect(Collectors.toList()));
        return Result.success(detail);
    }

    @SuppressWarnings("unchecked")
    private LlmChatResponse toChatResponse(AiLeaderSession session, Object ragResult) {
        Map<String, Object> result = ragResult instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> metadata = result.get("metadata") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        LlmChatResponse response = new LlmChatResponse();
        response.setSessionId(session.getSessionId());
        response.setModel("");
        response.setRagStrategy(stringValue(result.get("strategy")));
        response.setAgentName(LEADER_AGENT);
        response.setSearchKeyword("");
        response.setMatchedResults(List.of());
        response.setRetrievalMeta(metadata);
        response.setTrace(traceAsMaps(result.get("trace")));
        response.setAnswer(stringValue(result.get("answer")));
        response.setAnswerType(stringValue(result.get("answerType")));
        return response;
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }

    private AiLeaderSession getOrCreateSession(Long userId, String requestedSessionId, String firstInput) {
        String sessionId = StringUtils.hasText(requestedSessionId)
                ? requestedSessionId.trim()
                : "app-ai-" + UUID.randomUUID();
        return sessionRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseGet(() -> {
                    AiLeaderSession session = new AiLeaderSession();
                    session.setUserId(userId);
                    session.setSessionId(sessionId);
                    session.setTitle(buildTitle(firstInput));
                    session.setLastMessage("");
                    session.setMessageCount(0);
                    return sessionRepository.save(session);
                });
    }

    private void saveMessage(AiLeaderSession session, String role, String content, String answerType) {
        AiLeaderMessage message = new AiLeaderMessage();
        message.setLeaderSessionId(session.getId());
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setAnswerType(answerType);
        messageRepository.save(message);
    }

    private void refreshSession(AiLeaderSession session, String lastMessage) {
        session.setLastMessage(truncate(lastMessage, 500));
        session.setMessageCount((int) messageRepository.countByLeaderSessionId(session.getId()));
        sessionRepository.save(session);
    }

    private String buildTitle(String input) {
        String normalized = StringUtils.hasText(input) ? input.trim().replaceAll("\\s+", " ") : "新的 Leader 会话";
        return truncate(normalized, 40);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private AiLeaderSessionItem toSessionItem(AiLeaderSession session) {
        AiLeaderSessionItem item = new AiLeaderSessionItem();
        item.setSessionId(session.getSessionId());
        item.setTitle(session.getTitle());
        item.setLastMessage(session.getLastMessage());
        item.setMessageCount(session.getMessageCount());
        item.setCreateTime(session.getCreateTime());
        item.setUpdateTime(session.getUpdateTime());
        return item;
    }

    private AiLeaderMessageItem toMessageItem(AiLeaderMessage message) {
        AiLeaderMessageItem item = new AiLeaderMessageItem();
        item.setId(message.getId());
        item.setRole(message.getRole());
        item.setContent(message.getContent());
        item.setAnswerType(message.getAnswerType());
        item.setCreateTime(message.getCreateTime());
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> traceAsMaps(Object trace) {
        if (trace instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
