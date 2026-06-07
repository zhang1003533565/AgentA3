package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

@Service
public class PythonAiProxyService {
    private static final Logger log = LoggerFactory.getLogger(PythonAiProxyService.class);
    private static final String DEFAULT_AGENT_NAME = "leader_agent";
    private static final String AGENT_MODEL_BINDING_PREFIX = "ai.agent-bindings.";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final SystemConfigService systemConfigService;
    private final String pythonBaseUrl;
    private final long timeoutSeconds;
    private final int fileResponseMaxInMemoryBytes;

    public PythonAiProxyService(WebClient.Builder webClientBuilder,
                                ObjectMapper objectMapper,
                                JwtUtil jwtUtil,
                                SystemConfigService systemConfigService,
                                @Value("${ai.python.base-url:http://localhost:8081}") String pythonBaseUrl,
                                @Value("${ai.python.timeout-seconds:65}") long timeoutSeconds,
                                @Value("${ai.python.file-response-max-in-memory-bytes:52428800}") int fileResponseMaxInMemoryBytes) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
        this.systemConfigService = systemConfigService;
        this.pythonBaseUrl = pythonBaseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.fileResponseMaxInMemoryBytes = fileResponseMaxInMemoryBytes;
    }

    public LlmChatResponse chat(LlmChatRequest request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        String requestedModel = resolveRequestedModel(request.getLlmModel(), request.getAgentName());
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(buildUri("/internal/chat"))
                    .headers(headers -> applyPythonHeaders(headers, authorization, userId, requestedModel))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LlmChatResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python AI 服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python AI 服务调用失败: " + e.getMessage());
        }
    }

    public Object getRagStrategies(String authorization) {
        return getRagObject("/internal/rag/strategies", authorization);
    }

    public Object getRagStrategy(String strategyName, String authorization) {
        return getRagObject("/internal/rag/strategies/" + strategyName, authorization);
    }

    public Object getRagCapabilities(String authorization) {
        return getRagObject("/internal/rag/capabilities", authorization);
    }

    public Object getRagFramework(String authorization) {
        return getRagObject("/internal/rag/framework", authorization);
    }

    public Object getRagAgents(String authorization) {
        return getRagObject("/internal/rag/agents", authorization);
    }

    public Object getRagAgent(String agentName, String authorization) {
        return getRagObject("/internal/rag/agents/" + agentName, authorization);
    }

    public Object updateRagAgentExampleInput(String agentName, Map<String, Object> request, String authorization) {
        return putRagObject("/internal/rag/agents/" + agentName + "/example-input", request, authorization);
    }

    public Object getModelProviders(String authorization) {
        return getPythonAuthObject("/internal/models/providers", authorization, "Python 模型目录服务调用失败");
    }

    public Object queryRag(Map<String, Object> request, String authorization) {
        String requestedModel = resolveRequestedModel(request);
        if (!StringUtils.hasText(requestedModel)) {
            throw new BusinessException(Result.ERROR_CODE, "请选择已测试成功的模型后再执行智能体");
        }
        Map<String, Object> sanitized = sanitizeRagRequest(request);
        return postRagObject("/internal/rag/query", sanitized, authorization, requestedModel);
    }

    public SseEmitter streamRag(Map<String, Object> request, String authorization) {
        return streamRag(request, authorization, null);
    }

    public SseEmitter streamRag(Map<String, Object> request,
                                String authorization,
                                BiConsumer<String, Object> eventConsumer) {
        String requestedModel = resolveRequestedModel(request);
        if (!StringUtils.hasText(requestedModel)) {
            throw new BusinessException(Result.ERROR_CODE, "请选择已测试成功的模型后再执行智能体");
        }
        Map<String, Object> sanitized = sanitizeRagRequest(request);
        return streamPythonObject(
                "/internal/rag/query/stream",
                sanitized,
                authorization,
                requestedModel,
                eventConsumer,
                true
        );
    }

    public Object ingestRagDocuments(Map<String, Object> request, String authorization) {
        return postRagObject("/internal/rag/documents", request, authorization);
    }

    public Object convertPdf(MultipartFile file, String targetFormat, String authorization) {
        validateAuthorization(authorization);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Result.ERROR_CODE, "PDF 文件不能为空");
        }
        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document.pdf";
        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(Result.ERROR_CODE, "仅支持上传 PDF 文件");
        }
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            Map<String, Object> payload = Map.of(
                    "fileName", filename,
                    "targetFormat", targetFormat == null ? "" : targetFormat,
                    "contentBase64", Base64.getEncoder().encodeToString(file.getBytes())
            );
            return buildFileResponseWebClient()
                    .post()
                    .uri(buildUri("/internal/rag/pdf/convert"))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + e.getMessage());
        }
    }

    public Object convertPpt(MultipartFile file, String authorization) {
        validateAuthorization(authorization);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Result.ERROR_CODE, "PPTX 文件不能为空");
        }
        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "presentation.pptx";
        if (!filename.toLowerCase().endsWith(".pptx")) {
            throw new BusinessException(Result.ERROR_CODE, "当前仅支持上传 PPTX 文件；请先将 PPT 另存为 PPTX");
        }
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            Map<String, Object> payload = Map.of(
                    "fileName", filename,
                    "contentBase64", Base64.getEncoder().encodeToString(file.getBytes())
            );
            return buildFileResponseWebClient()
                    .post()
                    .uri(buildUri("/internal/rag/ppt/convert"))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + e.getMessage());
        }
    }

    public Object listRagDocuments(String authorization) {
        return getRagObject("/internal/rag/documents", authorization);
    }

    public Object getRagVectorStoreHealth(String authorization) {
        return getRagObject("/internal/rag/vector-store/health", authorization);
    }

    public Object getRagEmbeddingHealth(String authorization) {
        return getRagObject("/internal/rag/embedding/health", authorization);
    }

    public Object getRagGraphStoreHealth(String authorization) {
        return getRagObject("/internal/rag/graph-store/health", authorization);
    }

    public Object getTextToSqlSchema(String authorization) {
        return getRagObject("/internal/rag/text-to-sql/schema", authorization);
    }

    public Object executeTextToSql(Map<String, Object> request, String authorization) {
        return postRagObject("/internal/rag/text-to-sql/execute", request, authorization);
    }

    public Object evaluateRag(Map<String, Object> request, String authorization) {
        return postRagObject("/internal/rag/evaluate", request, authorization);
    }

    public Object generateImage(Map<String, Object> request, String authorization) {
        return postImageObject("/internal/images/generate", request, authorization);
    }

    public Object generateImagesBatch(Map<String, Object> request, String authorization) {
        return postImageObject("/internal/images/batch", request, authorization);
    }

    public Object getImageTask(String taskId, String authorization) {
        return getImageObject("/internal/images/tasks/" + taskId, authorization);
    }

    public Object generateVideo(Map<String, Object> request, String authorization) {
        return postVideoObject("/internal/videos/generate", request, authorization);
    }

    public Object testVisionModel(Map<String, Object> request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(buildUri("/internal/models/vision/test"))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request == null ? Map.of() : request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 视觉理解服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 视觉理解服务调用失败: " + e.getMessage());
        }
    }

    public Object generateVideosBatch(Map<String, Object> request, String authorization) {
        return postVideoObject("/internal/videos/batch", request, authorization);
    }

    public Object getVideoTask(String taskId, String authorization) {
        return getVideoObject("/internal/videos/tasks/" + taskId, authorization);
    }

    public SseEmitter streamChat(LlmChatRequest request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        String requestedModel = resolveRequestedModel(request.getLlmModel(), request.getAgentName());

        return streamPythonObject("/internal/chat/stream", request, authorization, userId, requestedModel, null);
    }

    private SseEmitter streamPythonObject(String path, Object request, String authorization, String requestedModel) {
        return streamPythonObject(path, request, authorization, requestedModel, null);
    }

    private SseEmitter streamPythonObject(String path,
                                         Object request,
                                         String authorization,
                                         String requestedModel,
                                         BiConsumer<String, Object> eventConsumer) {
        return streamPythonObject(path, request, authorization, requestedModel, eventConsumer, false);
    }

    private SseEmitter streamPythonObject(String path,
                                         Object request,
                                         String authorization,
                                         String requestedModel,
                                         BiConsumer<String, Object> eventConsumer,
                                         boolean ragHeaders) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        return streamPythonObject(path, request, authorization, userId, requestedModel, eventConsumer, ragHeaders);
    }

    private SseEmitter streamPythonObject(String path,
                                         Object request,
                                         String authorization,
                                         Long userId,
                                         String requestedModel,
                                         BiConsumer<String, Object> eventConsumer) {
        return streamPythonObject(path, request, authorization, userId, requestedModel, eventConsumer, false);
    }

    private SseEmitter streamPythonObject(String path,
                                         Object request,
                                         String authorization,
                                         Long userId,
                                         String requestedModel,
                                         BiConsumer<String, Object> eventConsumer,
                                         boolean ragHeaders) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                log.info("start python stream relay path={}", path);
                webClientBuilder.build()
                        .post()
                        .uri(buildUri(path))
                        .headers(headers -> {
                            if (ragHeaders) {
                                applyPythonHeadersForRag(headers, authorization, userId, requestedModel);
                            } else {
                                applyPythonHeaders(headers, authorization, userId, requestedModel);
                            }
                        })
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .doOnNext(event -> relaySseEvent(event, emitter, eventConsumer))
                        .blockLast();
                log.info("python stream relay completed path={}", path);
                emitter.complete();
            } catch (Exception e) {
                log.error("python stream relay failed path={}", path, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", "Python AI 流式服务异常: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void relaySseEvent(ServerSentEvent<String> sourceEvent,
                               SseEmitter emitter,
                               BiConsumer<String, Object> eventConsumer) {
        String eventName = sourceEvent.event();
        if (!StringUtils.hasText(eventName)) {
            eventName = "message";
        }
        String rawData = sourceEvent.data();
        Object payload = parsePayload(rawData);
        if (eventConsumer != null) {
            eventConsumer.accept(eventName, payload);
        }

        if (log.isDebugEnabled()) {
            log.debug("relay sse event event={} data={}", eventName, rawData);
        } else {
            log.info("relay sse event event={}", eventName);
        }

        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new RuntimeException("SSE 事件透传失败: " + e.getMessage(), e);
        }
    }

    private void validateAuthorization(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "未登录或Token无效");
        }
    }

    private Object getRagObject(String path, String authorization) {
        return getPythonAuthObject(path, authorization, "Python RAG 服务调用失败");
    }

    private Object getPythonObject(String path, String authorization, String errorPrefix) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .get()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonHeaders(headers, authorization, userId, null))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, errorPrefix + ": " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, errorPrefix + ": " + e.getMessage());
        }
    }

    private Object getPythonAuthObject(String path, String authorization, String errorPrefix) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .get()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, errorPrefix + ": " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, errorPrefix + ": " + e.getMessage());
        }
    }

    private Object postRagObject(String path, Map<String, Object> request, String authorization) {
        return postRagObject(path, request, authorization, null);
    }

    private Object postRagObject(String path, Map<String, Object> request, String authorization, String requestedModel) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonHeadersForRag(headers, authorization, userId, requestedModel))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request == null ? Map.of() : request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + e.getMessage());
        }
    }

    private Object putRagObject(String path, Map<String, Object> request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .put()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request == null ? Map.of() : request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python RAG 服务调用失败: " + e.getMessage());
        }
    }

    private Object getImageObject(String path, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .get()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 图片生成服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 图片生成服务调用失败: " + e.getMessage());
        }
    }

    private Object postImageObject(String path, Map<String, Object> request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request == null ? Map.of() : request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 图片生成服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 图片生成服务调用失败: " + e.getMessage());
        }
    }

    private Object getVideoObject(String path, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .get()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 视频生成服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 视频生成服务调用失败: " + e.getMessage());
        }
    }

    private Object postVideoObject(String path, Map<String, Object> request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(buildUri(path))
                    .headers(headers -> applyPythonAuthHeaders(headers, authorization, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request == null ? Map.of() : request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 视频生成服务调用失败: " + extractRemoteMessage(e));
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "Python 视频生成服务调用失败: " + e.getMessage());
        }
    }

    private Long extractUserId(String token) {
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "未登录或Token无效");
        }
    }

    private String normalizeBearerToken(String authorization) {
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    private String buildUri(String path) {
        String base = pythonBaseUrl.endsWith("/") ? pythonBaseUrl.substring(0, pythonBaseUrl.length() - 1) : pythonBaseUrl;
        return base + path;
    }

    private WebClient buildFileResponseWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(fileResponseMaxInMemoryBytes))
                .build();
        return webClientBuilder.clone()
                .exchangeStrategies(strategies)
                .build();
    }

    private void applyPythonHeaders(HttpHeaders headers, String authorization, Long userId, String requestedModel) {
        String configPrefix = resolveConfigPrefix(requestedModel);
        applyPythonAuthHeaders(headers, authorization, userId);
        headers.set("X-AI-Provider", requireAiConfig(configPrefix, "provider", "模型服务商"));
        headers.set("X-AI-Base-Url", requireAiConfig(configPrefix, "base-url", "模型服务地址"));
        headers.set("X-AI-Api-Key", requireAiConfig(configPrefix, "api-key", "模型服务密钥"));
        headers.set("X-AI-Model", requireAiConfig(configPrefix, "model", "模型 ID"));
    }

    private void applyPythonHeadersForRag(HttpHeaders headers, String authorization, Long userId, String requestedModel) {
        applyPythonAuthHeaders(headers, authorization, userId);
        if (!StringUtils.hasText(requestedModel)) {
            return;
        }
        String configPrefix = resolveConfigPrefix(requestedModel);
        headers.set("X-AI-Provider", requireAiConfig(configPrefix, "provider", "模型服务商"));
        headers.set("X-AI-Base-Url", requireAiConfig(configPrefix, "base-url", "模型服务地址"));
        headers.set("X-AI-Api-Key", requireAiConfig(configPrefix, "api-key", "模型服务密钥"));
        headers.set("X-AI-Model", requireAiConfig(configPrefix, "model", "模型 ID"));
    }

    private void applyPythonAuthHeaders(HttpHeaders headers, String authorization, Long userId) {
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        headers.set("X-User-Id", userId.toString());
    }

    private String resolveRequestedModel(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return resolveAgentBoundModel(null);
        }
        Object llmModel = request.get("llmModel");
        if (llmModel != null && StringUtils.hasText(String.valueOf(llmModel))) {
            return String.valueOf(llmModel).trim();
        }
        Object xAiModel = request.get("xAiModel");
        if (xAiModel != null && StringUtils.hasText(String.valueOf(xAiModel))) {
            return String.valueOf(xAiModel).trim();
        }
        Object agentName = request.get("agentName");
        return resolveAgentBoundModel(agentName == null ? null : String.valueOf(agentName));
    }

    private String resolveRequestedModel(String requestedModel, String agentName) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel.trim();
        }
        return resolveAgentBoundModel(agentName);
    }

    private String resolveAgentBoundModel(String agentName) {
        String normalizedAgent = StringUtils.hasText(agentName) ? agentName.trim() : DEFAULT_AGENT_NAME;
        String key = AGENT_MODEL_BINDING_PREFIX + normalizedAgent + ".model";
        String value = systemConfigService.getValue(key, "");
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> sanitizeRagRequest(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new HashMap<>(request);
        copy.remove("llmModel");
        copy.remove("xAiModel");
        return copy;
    }

    private String resolveConfigPrefix(String requestedModel) {
        if (!StringUtils.hasText(requestedModel)) {
            throw new BusinessException(Result.ERROR_CODE, "未指定模型配置，请从已配置模型中明确选择");
        }
        String trimmed = requestedModel.trim();
        if (!trimmed.startsWith("ai.service.")) {
            throw new BusinessException(Result.ERROR_CODE, "模型参数必须是 ai.service.* 配置前缀");
        }
        return trimmed;
    }

    private String requireAiConfig(String configPrefix, String field, String label) {
        String key = configPrefix + "." + field;
        String value = systemConfigService.getValue(key, "");
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(
                    Result.ERROR_CODE,
                    label + "未配置，请在系统配置中维护 " + key
            );
        }
        return value;
    }

    private String extractRemoteMessage(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return e.getMessage();
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Object detail = map.get("detail");
                if (detail != null) {
                    return detail.toString();
                }
                Object message = map.get("message");
                if (message != null) {
                    return message.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    private Object parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception ignored) {
            return Map.of("content", payload);
        }
    }
}
