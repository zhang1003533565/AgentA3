package com.example.appbackend.service.impl;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.LlmMemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LlmMemoryServiceImpl implements LlmMemoryService {

    private static final TypeReference<List<Map<String, String>>> HISTORY_TYPE = new TypeReference<>() {
    };

    private static final String USER_ROLE = "user";
    private static final String AI_ROLE = "ai";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlMinutes;
    private final int maxMessages;

    public LlmMemoryServiceImpl(StringRedisTemplate stringRedisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${llm.memory.ttl-minutes:120}") long ttlMinutes,
                                @Value("${llm.memory.max-messages:20}") int maxMessages) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttlMinutes = ttlMinutes;
        this.maxMessages = maxMessages;
    }

    @Override
    public String getOrCreateSessionId(String token, String requestedSessionId) {
        String sessionId = StringUtils.hasText(requestedSessionId) ? requestedSessionId : UUID.randomUUID().toString();
        String normalizedToken = normalizeToken(token);
        try {
            String sessionToken = buildSessionToken(sessionId, normalizedToken);
            String raw = stringRedisTemplate.opsForValue().get(historyKey(sessionToken));
            if (!StringUtils.hasText(raw)) {
                stringRedisTemplate.opsForValue().set(historyKey(sessionToken), "[]", ttl());
            } else {
                stringRedisTemplate.expire(historyKey(sessionToken), ttl());
            }
            return sessionId;
        } catch (DataAccessException e) {
            throw new BusinessException(Result.ERROR_CODE, "Redis 不可用，短期记忆服务暂时无法使用");
        }
    }

    @Override
    public String resolveSessionToken(String token, String sessionId) {
        return buildSessionToken(sessionId, normalizeToken(token));
    }

    @Override
    public List<Map<String, String>> getHistoryMessages(String sessionToken) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(historyKey(sessionToken));
            stringRedisTemplate.expire(historyKey(sessionToken), ttl());
            if (!StringUtils.hasText(raw)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(raw, HISTORY_TYPE);
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "读取短期记忆失败");
        }
    }

    @Override
    public void appendConversation(String sessionToken, String userInput, String assistantAnswer) {
        try {
            List<Map<String, String>> history = getHistoryMessages(sessionToken);
            history.add(message(USER_ROLE, userInput));
            history.add(message(AI_ROLE, assistantAnswer));

            while (history.size() > maxMessages) {
                history.remove(0);
            }

            stringRedisTemplate.opsForValue().set(historyKey(sessionToken), objectMapper.writeValueAsString(history), ttl());
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "保存短期记忆失败");
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private Duration ttl() {
        return Duration.ofMinutes(ttlMinutes);
    }

    private String historyKey(String sessionToken) {
        return "llm:memory:" + sessionToken;
    }

    private String buildSessionToken(String sessionId, String token) {
        return sessionId + "_" + sha256(token);
    }

    private String normalizeToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "未登录或Token无效");
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(Result.ERROR_CODE, "生成会话标识失败");
        }
    }
}
