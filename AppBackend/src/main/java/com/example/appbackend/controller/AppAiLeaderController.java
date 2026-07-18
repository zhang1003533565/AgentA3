package com.example.appbackend.controller;

import com.example.appbackend.dto.AiLeaderMessageItem;
import com.example.appbackend.dto.AiLeaderSessionDetail;
import com.example.appbackend.dto.AiLeaderSessionItem;
import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.AiLeaderGeneratedExport;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderGeneratedExportRepository;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.service.UserProfileService;
import com.example.appbackend.service.impl.AssistantEnvelopeService;
import com.example.appbackend.service.impl.PythonAiProxyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai/leader")
@Tag(name = "App Leader 智能助手", description = "App 端固定接入 Leader 智能体")
public class AppAiLeaderController {

    private static final Logger log = LoggerFactory.getLogger(AppAiLeaderController.class);
    private static final String LEADER_AGENT = "leader_agent";
    private static final String SAFE_STREAM_FAILURE_MESSAGE = "资源生成失败，请稍后再试。";
    private static final Set<String> STRUCTURED_PAYLOAD_EVENTS = Set.of("generation_start", "error", "done");

    private final PythonAiProxyService pythonAiProxyService;
    private final AiLeaderSessionRepository sessionRepository;
    private final AiLeaderMessageRepository messageRepository;
    private final AiLeaderGeneratedExportRepository exportRepository;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;
    private final AssistantEnvelopeService assistantEnvelopeService;

    public AppAiLeaderController(PythonAiProxyService pythonAiProxyService,
                                 AiLeaderSessionRepository sessionRepository,
                                 AiLeaderMessageRepository messageRepository,
                                 AiLeaderGeneratedExportRepository exportRepository,
                                 UserProfileService userProfileService,
                                 ObjectMapper objectMapper,
                                 AssistantEnvelopeService assistantEnvelopeService) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.exportRepository = exportRepository;
        this.userProfileService = userProfileService;
        this.objectMapper = objectMapper;
        this.assistantEnvelopeService = assistantEnvelopeService;
    }

    @PostMapping("/query")
    @Operation(summary = "App 智能助手查询", description = "固定调用 Leader 智能体，不允许 App 端切换其它智能体")
    public Result<LlmChatResponse> query(@Valid @RequestBody LlmChatRequest request, HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        AiLeaderSession session = getOrCreateSession(userId, request.getSessionId(), request.getInput());
        String visibleInput = visibleUserInput(request);
        saveUserMessage(session, request, visibleInput);
        refreshSession(session, visibleInput);

        Map<String, Object> payload = buildLeaderPayload(request, session.getSessionId(), userId, httpRequest.getHeader("Authorization"));

        Object ragResult = pythonAiProxyService.queryRag(payload, httpRequest.getHeader("Authorization"));
        LlmChatResponse response = toChatResponse(session, ragResult);
        AssistantEnvelopeService.PreparedEnvelope envelope = assistantEnvelopeService.prepareLiveResponse(
                response, mapValue(ragResult), request.getInput());
        saveAssistantMessage(userId, session, response, envelope);
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
        String visibleInput = visibleUserInput(request);
        saveUserMessage(session, request, visibleInput);
        refreshSession(session, visibleInput);

        Map<String, Object> payload = buildLeaderPayload(request, session.getSessionId(), userId, authorization);
        AtomicReference<AiLeaderMessage> visibleGenerationMessage = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean poisoned = new AtomicBoolean(false);
        Set<String> internalCapabilities = new LinkedHashSet<>();
        Object streamStateLock = new Object();
        return pythonAiProxyService.streamRag(payload, authorization, (eventName, eventPayload) -> {
            synchronized (streamStateLock) {
                if (poisoned.get()) {
                    return false;
                }
                AssistantEnvelopeService.CapabilityScan capabilityScan =
                        assistantEnvelopeService.scanInternalCapabilities(eventPayload);
                if (capabilityScan.malformed()) {
                    poisoned.set(true);
                    return false;
                }
                AssistantEnvelopeService.CapabilityScan cumulativeCapabilities =
                        assistantEnvelopeService.mergeInternalCapabilities(
                                internalCapabilities, capabilityScan.values());
                if (cumulativeCapabilities.malformed()) {
                    poisoned.set(true);
                    return false;
                }
                internalCapabilities.clear();
                internalCapabilities.addAll(cumulativeCapabilities.values());
                if (STRUCTURED_PAYLOAD_EVENTS.contains(eventName) && !(eventPayload instanceof Map<?, ?>)) {
                    return false;
                }
                if (completed.get()) {
                    return false;
                }
                if ("generation_start".equals(eventName)) {
                    LlmChatResponse response = toChatResponse(session, eventPayload);
                    AssistantEnvelopeService.PreparedEnvelope envelope = assistantEnvelopeService.prepareLiveResponse(
                            response, mapValue(eventPayload), request.getInput(), Set.copyOf(internalCapabilities));
                    AiLeaderMessage existing = visibleGenerationMessage.get();
                    AiLeaderMessage saved = existing == null
                            ? saveAssistantMessage(userId, session, response, envelope)
                            : updateAssistantMessage(userId, session, existing, response, envelope);
                    visibleGenerationMessage.set(saved);
                    assistantEnvelopeService.overwriteSsePayload(eventPayload, response);
                    refreshSession(session, response.getAnswer());
                    return true;
                }
                if ("error".equals(eventName)) {
                    Map<String, Object> errorResult = new LinkedHashMap<>();
                    errorResult.put("answer", SAFE_STREAM_FAILURE_MESSAGE);
                    errorResult.put("answerType", "text");
                    errorResult.put("outputType", "text");
                    errorResult.put("outputTypes", List.of("text"));
                    LlmChatResponse response = toChatResponse(session, errorResult);
                    AssistantEnvelopeService.PreparedEnvelope envelope = assistantEnvelopeService.prepareLiveResponse(
                            response, errorResult, request.getInput(), Set.copyOf(internalCapabilities));
                    AiLeaderMessage existing = visibleGenerationMessage.get();
                    AiLeaderMessage saved = existing == null
                            ? saveAssistantMessage(userId, session, response, envelope)
                            : updateAssistantMessage(userId, session, existing, response, envelope);
                    visibleGenerationMessage.set(saved);
                    completed.set(true);
                    assistantEnvelopeService.overwriteSsePayload(eventPayload, response);
                    refreshSession(session, SAFE_STREAM_FAILURE_MESSAGE);
                    return true;
                }
                if (!"done".equals(eventName)) {
                    if (!(eventPayload instanceof Map<?, ?>)) {
                        return false;
                    }
                    assistantEnvelopeService.sanitizeSseEventPayload(
                            eventName, eventPayload, Set.copyOf(internalCapabilities));
                    return true;
                }
                LlmChatResponse response = toChatResponse(session, eventPayload);
                AssistantEnvelopeService.PreparedEnvelope envelope = assistantEnvelopeService.prepareLiveResponse(
                        response, mapValue(eventPayload), request.getInput(), Set.copyOf(internalCapabilities));
                AiLeaderMessage existing = visibleGenerationMessage.get();
                if (existing == null) {
                    AiLeaderMessage saved = saveAssistantMessage(userId, session, response, envelope);
                    visibleGenerationMessage.set(saved);
                } else {
                    updateAssistantMessage(userId, session, existing, response, envelope);
                }
                completed.set(true);
                assistantEnvelopeService.overwriteSsePayload(eventPayload, response);
                refreshSession(session, response.getAnswer());
                captureLeaderProfileEvidence(userId, session, request, response);
                return true;
            }
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
        List<AiLeaderMessageItem> items = new ArrayList<>();
        String latestUserInput = null;
        for (AiLeaderMessage message : messageRepository
                .findByLeaderSessionIdOrderByCreateTimeAscIdAsc(session.getId())) {
            if (AiLeaderMessage.ROLE_USER.equals(message.getRole())) {
                Map<String, Object> userMeta = readMap(message.getOutputMetaJson());
                String originalInput = stringValue(userMeta.get("requestContent"));
                latestUserInput = message.getAnswerType() != null
                        && message.getAnswerType().startsWith("action_")
                        && StringUtils.hasText(originalInput)
                        ? originalInput
                        : message.getContent();
            }
            items.add(toMessageItem(message, latestUserInput));
        }
        detail.setMessages(items);
        return Result.success(detail);
    }

    @GetMapping("/sessions/{sessionId}/messages/{messageId}/exports/{storageKey}")
    @Operation(summary = "下载 App Leader 生成文件")
    public ResponseEntity<byte[]> downloadExport(@PathVariable String sessionId,
                                                 @PathVariable Long messageId,
                                                 @PathVariable String storageKey,
                                                 HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        AiLeaderSession session = sessionRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseThrow(() -> new BusinessException(404, "导出文件不存在"));
        AiLeaderGeneratedExport manifest = exportRepository
                .findByUserIdAndLeaderSessionIdAndMessageIdAndStorageKeyAndStatus(
                        userId,
                        session.getId(),
                        messageId,
                        storageKey,
                        AiLeaderGeneratedExport.STATUS_ACTIVE
                )
                .orElseThrow(() -> new BusinessException(404, "导出文件不存在"));
        if (manifest.getExpiresAt() == null || !manifest.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(410, "导出文件已过期");
        }

        PythonAiProxyService.GeneratedExportResponse exported = pythonAiProxyService
                .downloadGeneratedExport(manifest.getStorageKey(), manifest.getPythonCapability());
        byte[] bytes = exported == null || exported.bytes() == null ? new byte[0] : exported.bytes();
        if (manifest.getSize() == null
                || manifest.getSize() != (long) bytes.length
                || !sha256Hex(bytes).equals(manifest.getSha256())) {
            throw new BusinessException(409, "导出文件完整性校验失败");
        }

        String disposition = ContentDisposition.attachment()
                .filename(safeDownloadFileName(manifest.getFileName(), manifest.getStorageKey()), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(safeContentType(manifest.getMimeType()))
                .contentLength(bytes.length)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
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

    private MediaType safeContentType(String manifestType) {
        try {
            MediaType parsed = MediaType.parseMediaType(manifestType);
            return isConcreteMediaType(parsed) ? parsed : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private boolean isConcreteMediaType(MediaType mediaType) {
        return mediaType != null && !mediaType.isWildcardType() && !mediaType.isWildcardSubtype();
    }

    private String safeDownloadFileName(String fileName, String storageKey) {
        String source = StringUtils.hasText(fileName) ? fileName : storageKey;
        if (!StringUtils.hasText(source)) {
            return "download";
        }
        StringBuilder sanitized = new StringBuilder(source.length());
        String reserved = "\\/:*?\"<>|";
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            sanitized.append(Character.isISOControl(value) || reserved.indexOf(value) >= 0 ? '_' : value);
        }
        String result = sanitized.toString().trim();
        while (result.contains("..")) {
            result = result.replace("..", "_");
        }
        return StringUtils.hasText(result) ? result : "download";
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Map<String, Object> buildLeaderPayload(LlmChatRequest request, String sessionId, Long userId, String authorization) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("input", request.getInput());
        payload.put("agentName", LEADER_AGENT);
        if (StringUtils.hasText(request.getLlmModel())) {
            payload.put("llmModel", request.getLlmModel().trim());
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "app_ai_assistant");
        metadata.put("sessionId", sessionId == null ? "" : sessionId);
        if (StringUtils.hasText(request.getInteractionType())) {
            metadata.put("interactionType", request.getInteractionType().trim());
            if (StringUtils.hasText(request.getRequestedOutputType())) {
                metadata.put("requestedOutputType", request.getRequestedOutputType().trim());
            }
            if (request.getSourceMessageId() != null) {
                metadata.put("sourceMessageId", request.getSourceMessageId());
            }
        }
        // Leader chat only reads the compatible saved AI insight (or the local fallback) here.
        // A missing/stale insight is refreshed in a bounded background executor below, so opening
        // an SSE stream never waits for profile_summary_agent.
        long profileContextStartedAt = System.nanoTime();
        Map<String, Object> profileContext = userProfileService.buildLeaderProfileContext(userId);
        metadata.put("profileSnapshot", profileContext);
        metadata.put("profileContextMs", Math.max(0L, (System.nanoTime() - profileContextStartedAt) / 1_000_000L));
        boolean hasSavedAiSnapshot = "profile_summary_agent".equals(profileContext.get("summaryEngine"));
        metadata.put("profileContextSource", hasSavedAiSnapshot ? "saved_ai_snapshot" : "local_snapshot");
        if (!hasSavedAiSnapshot) {
            userProfileService.refreshLeaderProfileContextAsync(userId, authorization);
        }
        metadata.put("profileEvidencePolicy", Map.of(
                "leaderCanUpdateScore", false,
                "leaderCanSubmitEvidence", true,
                "evidenceEndpoint", "POST /api/profile/evidence",
                "updateMode", "行为证据实时记录，画像分数定时汇总更新"
        ));
        payload.put("metadata", metadata);
        return payload;
    }

    private String visibleUserInput(LlmChatRequest request) {
        if (request != null
                && StringUtils.hasText(request.getInteractionType())
                && StringUtils.hasText(request.getDisplayInput())) {
            return truncate(request.getDisplayInput().trim(), 160);
        }
        return request == null ? "" : request.getInput();
    }

    private String userMessageAnswerType(LlmChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getInteractionType())) {
            return "text";
        }
        return "action_" + request.getInteractionType().trim();
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
        String chosenOutputFormat = inferChosenOutputFormat(input);

        if ("document".equals(chosenOutputFormat)) {
            evidenceList.add(buildLeaderEvidence(
                    session,
                    response,
                    "resource_preference",
                    "increase",
                    3,
                    "文件/文档",
                    "用户在 Leader 对话中选择文件或文档形式：" + truncate(input, 520),
                    List.of("文件", "文档", "输出形式偏好")
            ));
        } else if ("image".equals(chosenOutputFormat)) {
            evidenceList.add(buildLeaderEvidence(
                    session,
                    response,
                    "resource_preference",
                    "increase",
                    3,
                    "图片/图解",
                    "用户在 Leader 对话中选择图片或图解形式：" + truncate(input, 520),
                    List.of("图片", "图解", "输出形式偏好")
            ));
        }

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
        if (!StringUtils.hasText(chosenOutputFormat)
                && containsAny(input, "图解", "图片", "流程图", "思维导图", "视频", "代码", "例子", "案例", "文件", "文档", "word", "pdf", "ppt", "markdown", "表格")) {
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

    private String inferChosenOutputFormat(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        String normalized = input.replaceAll("\\s+", "").toLowerCase();
        boolean rejectImage = containsAny(normalized, "不要图片", "不用图片", "不是图片", "别发图片", "不需要图片");
        boolean rejectDocument = containsAny(normalized, "不要文件", "不用文件", "不是文件", "别发文件", "不需要文件", "不要文档");
        boolean wantsDocument = containsAny(
                normalized,
                "要文件", "文件形式", "文件版", "推送文件", "发文件", "生成文件",
                "要文档", "文档形式", "文档版", "word", "docx", "pdf", "ppt", "excel", "表格", "下载版"
        );
        boolean wantsImage = containsAny(
                normalized,
                "要图片", "图片形式", "图片版", "推送图片", "发图片", "生成图片",
                "要图解", "图解形式", "图解版", "配图", "海报", "png", "jpg"
        );
        if (wantsDocument && !rejectDocument && (!wantsImage || rejectImage)) {
            return "document";
        }
        if (wantsImage && !rejectImage && (!wantsDocument || rejectDocument)) {
            return "image";
        }
        return "";
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

    private void saveUserMessage(AiLeaderSession session, LlmChatRequest request, String visibleInput) {
        AiLeaderMessage message = new AiLeaderMessage();
        message.setLeaderSessionId(session.getId());
        message.setRole(AiLeaderMessage.ROLE_USER);
        message.setContent(visibleInput == null ? "" : visibleInput);
        message.setAnswerType(userMessageAnswerType(request));
        if (request != null && StringUtils.hasText(request.getInteractionType())) {
            Map<String, Object> actionMeta = new LinkedHashMap<>();
            actionMeta.put("interactionType", request.getInteractionType().trim());
            actionMeta.put("requestContent", request.getInput());
            if (StringUtils.hasText(request.getRequestedOutputType())) {
                actionMeta.put("requestedOutputType", request.getRequestedOutputType().trim());
            }
            if (request.getSourceMessageId() != null) {
                actionMeta.put("sourceMessageId", request.getSourceMessageId());
            }
            try {
                message.setOutputMetaJson(objectMapper.writeValueAsString(actionMeta));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("Unable to persist structured assistant action", error);
            }
        }
        messageRepository.save(message);
    }

    private AiLeaderMessage saveAssistantMessage(Long userId,
                                                 AiLeaderSession session,
                                                 LlmChatResponse response,
                                                 AssistantEnvelopeService.PreparedEnvelope envelope) {
        return assistantEnvelopeService.persistAssistantMessage(
                userId, session, response, envelope.internalAttachments(), envelope.internalCapabilities(), null);
    }

    private AiLeaderMessage updateAssistantMessage(Long userId,
                                                   AiLeaderSession session,
                                                   AiLeaderMessage message,
                                                   LlmChatResponse response,
                                                   AssistantEnvelopeService.PreparedEnvelope envelope) {
        return assistantEnvelopeService.persistAssistantMessage(
                userId, session, response, envelope.internalAttachments(), envelope.internalCapabilities(), message);
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

    private AiLeaderMessageItem toMessageItem(AiLeaderMessage message, String expectedQuery) {
        AiLeaderMessageItem item = new AiLeaderMessageItem();
        item.setId(message.getId());
        item.setRole(message.getRole());
        item.setContent(message.getContent());
        item.setAnswerType(message.getAnswerType());
        item.setOutputType(message.getOutputType());
        item.setAgentName(message.getAgentName());
        item.setSearchKeyword(message.getSearchKeyword());
        item.setOutputTypes(readStringList(message.getOutputTypesJson()));
        item.setOutputMeta(readMap(message.getOutputMetaJson()));
        item.setRetrievalMeta(readMap(message.getRetrievalMetaJson()));
        item.setTrace(readMapList(message.getTraceJson()));
        if (AiLeaderMessage.ROLE_ASSISTANT.equals(message.getRole())) {
            assistantEnvelopeService.restoreEnvelope(message, item, expectedQuery);
        }
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

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception error) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception error) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception error) {
            return List.of();
        }
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
