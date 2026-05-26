package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class PythonAiProxyService {
    private static final Logger log = LoggerFactory.getLogger(PythonAiProxyService.class);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final String pythonBaseUrl;
    private final long timeoutSeconds;

    public PythonAiProxyService(WebClient.Builder webClientBuilder,
                                ObjectMapper objectMapper,
                                JwtUtil jwtUtil,
                                @Value("${ai.python.base-url:http://localhost:8081}") String pythonBaseUrl,
                                @Value("${ai.python.timeout-seconds:65}") long timeoutSeconds) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
        this.pythonBaseUrl = pythonBaseUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    public LlmChatResponse chat(LlmChatRequest request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(buildUri("/internal/chat"))
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header("X-User-Id", userId.toString())
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

    public Object queryRag(Map<String, Object> request, String authorization) {
        return postRagObject("/internal/rag/query", request, authorization);
    }

    public Object ingestRagDocuments(Map<String, Object> request, String authorization) {
        return postRagObject("/internal/rag/documents", request, authorization);
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

    public SseEmitter streamChat(LlmChatRequest request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);

        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                log.info("start python stream relay sessionId={}", request.getSessionId());
                webClientBuilder.build()
                        .post()
                        .uri(buildUri("/internal/chat/stream"))
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .doOnNext(event -> relaySseEvent(event, emitter))
                        .blockLast();
                log.info("python stream relay completed sessionId={}", request.getSessionId());
                emitter.complete();
            } catch (Exception e) {
                log.error("python stream relay failed sessionId={}", request.getSessionId(), e);
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

    private void relaySseEvent(ServerSentEvent<String> sourceEvent, SseEmitter emitter) {
        String eventName = sourceEvent.event();
        if (!StringUtils.hasText(eventName)) {
            eventName = "message";
        }
        String rawData = sourceEvent.data();
        Object payload = parsePayload(rawData);

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
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .get()
                    .uri(buildUri(path))
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header("X-User-Id", userId.toString())
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

    private Object postRagObject(String path, Map<String, Object> request, String authorization) {
        validateAuthorization(authorization);
        String token = normalizeBearerToken(authorization);
        Long userId = extractUserId(token);
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(buildUri(path))
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header("X-User-Id", userId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request == null ? Map.of() : request)
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
