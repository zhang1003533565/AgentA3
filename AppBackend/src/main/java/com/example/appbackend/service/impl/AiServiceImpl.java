package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AiWriteDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AiService;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    public AiServiceImpl(SystemConfigService systemConfigService, ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiWriteDTO.WriteResponse write(AiWriteDTO.WriteRequest request) {
        String apiKey = systemConfigService.getValue("ai.service.api-key", "");
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(400, "AI Key 未配置");
        }

        String baseUrl = trimTrailingSlash(systemConfigService.getValue("ai.service.base-url", DEFAULT_BASE_URL));
        String model = systemConfigService.getValue("ai.service.model", DEFAULT_MODEL);
        String prompt = buildPrompt(request);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(message));
        payload.put("temperature", 0.7);

        try {
            String responseText = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseText);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BusinessException(500, "AI 未返回可用内容");
            }

            AiWriteDTO.WriteResponse response = new AiWriteDTO.WriteResponse();
            response.setContent(content.trim());
            response.setModel(model);
            return response;
        } catch (WebClientResponseException error) {
            String detail = error.getResponseBodyAsString();
            throw new BusinessException(500, "AI 请求失败: " + detail);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 写作失败: " + error.getMessage());
        }
    }

    private String buildPrompt(AiWriteDTO.WriteRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据以下要求完成中文写作。");
        if (request.getTone() != null && !request.getTone().isBlank()) {
            builder.append("\n语气要求：").append(request.getTone()).append("。");
        }
        if (request.getWordCount() != null && !request.getWordCount().isBlank() && !"自动".equals(request.getWordCount())) {
            builder.append("\n字数要求：").append(request.getWordCount()).append("。");
        }
        if (request.getModelName() != null && !request.getModelName().isBlank()) {
            builder.append("\n前端选择模型：").append(request.getModelName()).append("。");
        }
        builder.append("\n写作需求：").append(request.getPrompt());
        builder.append("\n请直接输出正文，不要附加解释。");
        return builder.toString();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return DEFAULT_BASE_URL;
        return value.replaceAll("/+$", "");
    }
}
