package com.example.appbackend.controller;

import com.example.appbackend.dto.AiLeaderMessageItem;
import com.example.appbackend.dto.AiLeaderSessionDetail;
import com.example.appbackend.dto.AiLeaderSessionItem;
import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.service.UserProfileService;
import com.example.appbackend.service.impl.PythonAiProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai/leader")
@Tag(name = "App Leader 智能助手", description = "App 端固定接入 Leader 智能体")
public class AppAiLeaderController {

    private static final Logger log = LoggerFactory.getLogger(AppAiLeaderController.class);
    private static final String LEADER_AGENT = "leader_agent";

    private final PythonAiProxyService pythonAiProxyService;
    private final AiLeaderSessionRepository sessionRepository;
    private final AiLeaderMessageRepository messageRepository;
    private final UserProfileService userProfileService;

    public AppAiLeaderController(PythonAiProxyService pythonAiProxyService,
                                 AiLeaderSessionRepository sessionRepository,
                                 AiLeaderMessageRepository messageRepository,
                                 UserProfileService userProfileService) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userProfileService = userProfileService;
    }

    @PostMapping("/query")
    @Operation(summary = "App 智能助手查询", description = "固定调用 Leader 智能体，不允许 App 端切换其它智能体")
    public Result<LlmChatResponse> query(@Valid @RequestBody LlmChatRequest request, HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        AiLeaderSession session = getOrCreateSession(userId, request.getSessionId(), request.getInput());
        saveMessage(session, AiLeaderMessage.ROLE_USER, request.getInput(), "text");
        refreshSession(session, request.getInput());

        Map<String, Object> payload = buildLeaderPayload(request, session.getSessionId(), userId);

        Object ragResult = pythonAiProxyService.queryRag(payload, httpRequest.getHeader("Authorization"));
        LlmChatResponse response = toChatResponse(session, ragResult);
        saveMessage(session, AiLeaderMessage.ROLE_ASSISTANT, response.getAnswer(), response.getAnswerType());
        refreshSession(session, response.getAnswer());
        captureLeaderProfileEvidence(userId, session, request, response);
        return Result.success(response);
    }

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "App 智能助手流式查询", description = "固定调用 Leader 智能体，并以 SSE 增量返回回答")
    public SseEmitter queryStream(@Valid @RequestBody LlmChatRequest request,
                                  @RequestHeader(value = "Authorization", required = false) String authorization,
                                  HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        AiLeaderSession session = getOrCreateSession(userId, request.getSessionId(), request.getInput());
        saveMessage(session, AiLeaderMessage.ROLE_USER, request.getInput(), "text");
        refreshSession(session, request.getInput());

        Map<String, Object> payload = buildLeaderPayload(request, session.getSessionId(), userId);
        return pythonAiProxyService.streamRag(payload, authorization, (eventName, eventPayload) -> {
            if (!"done".equals(eventName)) {
                return;
            }
            LlmChatResponse response = toChatResponse(session, eventPayload);
            saveMessage(session, AiLeaderMessage.ROLE_ASSISTANT, response.getAnswer(), response.getAnswerType());
            refreshSession(session, response.getAnswer());
            captureLeaderProfileEvidence(userId, session, request, response);
        });
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
        Object metadataValue = result.containsKey("metadata") ? result.get("metadata") : result.get("retrievalMeta");
        Map<String, Object> metadata = metadataValue instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        LlmChatResponse response = new LlmChatResponse();
        response.setSessionId(session.getSessionId());
        response.setModel("");
        response.setRagStrategy(firstNonBlank(
                stringValue(result.get("strategy")),
                stringValue(result.get("ragStrategy"))
        ));
        response.setAgentName(firstNonBlank(
                stringValue(metadata.get("executedAgent")),
                stringValue(metadata.get("targetAgent")),
                stringValue(result.get("agentName")),
                LEADER_AGENT
        ));
        response.setSearchKeyword(stringValue(result.get("searchKeyword")));
        response.setMatchedResults(traceAsMaps(result.containsKey("documents")
                ? result.get("documents")
                : result.get("matchedResults")));
        response.setRetrievalMeta(metadata);
        response.setTrace(traceAsMaps(result.get("trace")));
        response.setAnswer(stringValue(result.get("answer")));
        response.setAnswerType(firstNonBlank(stringValue(result.get("answerType")), "text"));
        response.setOutputType(firstNonBlank(
                stringValue(result.get("outputType")),
                stringValue(metadata.get("outputType")),
                response.getAnswerType()
        ));
        response.setOutputTypes(stringList(result.containsKey("outputTypes")
                ? result.get("outputTypes")
                : metadata.get("outputTypes")));
        response.setOutputMeta(mapValue(result.get("outputMeta")));
        response.setAttachments(traceAsMaps(result.get("attachments")));
        return response;
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }

    private Map<String, Object> buildLeaderPayload(LlmChatRequest request, String sessionId, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("input", request.getInput());
        payload.put("agentName", LEADER_AGENT);
        if (StringUtils.hasText(request.getLlmModel())) {
            payload.put("llmModel", request.getLlmModel().trim());
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "app_ai_assistant");
        metadata.put("sessionId", sessionId == null ? "" : sessionId);
        metadata.put("profileSnapshot", userProfileService.buildLeaderProfileContext(userId));
        metadata.put("profileEvidencePolicy", Map.of(
                "leaderCanUpdateScore", false,
                "leaderCanSubmitEvidence", true,
                "evidenceEndpoint", "POST /api/profile/evidence",
                "updateMode", "行为证据实时记录，画像分数定时汇总更新"
        ));
        payload.put("metadata", metadata);
        return payload;
    }

    private void captureLeaderProfileEvidence(Long userId,
                                              AiLeaderSession session,
                                              LlmChatRequest request,
                                              LlmChatResponse response) {
        String input = request == null ? "" : request.getInput();
        if (!StringUtils.hasText(input)) {
            return;
        }
        List<UserProfileDTO.EvidenceRequest> evidenceList = new ArrayList<>();
        String objectName = inferObjectName(input);

        if (containsAny(input, "不会", "不懂", "没懂", "不清楚", "薄弱", "卡住", "错题", "哪里错", "看不懂")) {
            evidenceList.add(buildLeaderEvidence(
                    session,
                    response,
                    "weak_points",
                    "weakness",
                    -2,
                    objectName,
                    "用户在 Leader 对话中明确暴露薄弱点：" + truncate(input, 520),
                    List.of(objectName, "薄弱点")
            ));
        }
        if (containsAny(input, "备考", "考试", "复习", "考研", "期末", "项目", "作业", "面试", "论文", "毕业设计")) {
            evidenceList.add(buildLeaderEvidence(
                    session,
                    response,
                    "learning_goal",
                    "increase",
                    2,
                    objectName,
                    "用户在 Leader 对话中明确表达学习目标：" + truncate(input, 520),
                    List.of(objectName, "学习目标")
            ));
        }
        if (containsAny(input, "图解", "图片", "流程图", "思维导图", "视频", "代码", "例子", "案例", "文档", "markdown", "表格")) {
            evidenceList.add(buildLeaderEvidence(
                    session,
                    response,
                    "resource_preference",
                    "increase",
                    2,
                    objectName,
                    "用户在 Leader 对话中表达资源形式偏好：" + truncate(input, 520),
                    List.of(objectName, "资源偏好")
            ));
        }
        if (evidenceList.isEmpty() && isStudyAgent(response == null ? "" : response.getAgentName())) {
            evidenceList.add(buildLeaderEvidence(
                    session,
                    response,
                    "learning_goal",
                    "increase",
                    1,
                    objectName,
                    "Leader 路由到学习/题库类智能体，作为弱学习目标候选证据：" + truncate(input, 520),
                    List.of(objectName, "Leader 路由")
            ));
        }

        for (UserProfileDTO.EvidenceRequest evidence : evidenceList) {
            try {
                userProfileService.addEvidence(userId, evidence);
            } catch (Exception error) {
                log.warn("leader profile evidence skipped userId={} sessionId={}: {}", userId, session.getSessionId(), error.getMessage());
            }
        }
    }

    private UserProfileDTO.EvidenceRequest buildLeaderEvidence(AiLeaderSession session,
                                                               LlmChatResponse response,
                                                               String dimensionKey,
                                                               String direction,
                                                               int suggestedDelta,
                                                               String objectName,
                                                               String evidenceText,
                                                               List<String> evidenceTags) {
        UserProfileDTO.EvidenceRequest evidence = new UserProfileDTO.EvidenceRequest();
        evidence.setDimensionKey(dimensionKey);
        evidence.setSourceType("chat");
        evidence.setSourceId(session.getSessionId());
        evidence.setAction("expressed");
        evidence.setObjectType("conversation");
        evidence.setObjectId(session.getSessionId());
        evidence.setObjectName(objectName);
        evidence.setEvidence(truncate(evidenceText, 1000));
        evidence.setDirection(direction);
        evidence.setSuggestedDelta(suggestedDelta);
        evidence.setEvidenceTags(evidenceTags.stream().filter(StringUtils::hasText).distinct().limit(6).toList());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("submitter", "AppAiLeaderController");
        metadata.put("agentName", response == null ? "" : response.getAgentName());
        metadata.put("answerType", response == null ? "" : response.getAnswerType());
        metadata.put("outputTypes", response == null ? List.of() : response.getOutputTypes());
        metadata.put("capturePolicy", "only_explicit_chat_signal_or_weak_leader_route");
        evidence.setMetadata(metadata);
        return evidence;
    }

    private boolean isStudyAgent(String agentName) {
        String normalized = agentName == null ? "" : agentName.trim();
        return normalized.equals("textbook_knowledge_agent") || normalized.startsWith("textbook_question_");
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::stringValue)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of();
    }

    private boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String inferObjectName(String input) {
        if (!StringUtils.hasText(input)) {
            return "Leader 对话";
        }
        List<String> knownTopics = List.of(
                "循环队列", "栈", "队列", "链表", "二叉树", "排序", "数据结构",
                "Java", "Python", "数据库", "SQL", "前端", "后端",
                "PPT", "文档", "思维导图", "流程图", "图解", "代码",
                "考试", "复习", "项目", "作业", "论文", "面试"
        );
        for (String topic : knownTopics) {
            if (input.contains(topic)) {
                return topic;
            }
        }
        return truncate(input.trim().replaceAll("\\s+", " "), 40);
    }
}
