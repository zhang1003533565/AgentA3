package com.example.appbackend.service.impl;

import com.example.appbackend.service.WeeklyJobRecommendationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RagWeeklyJobRecommendationClient implements WeeklyJobRecommendationClient {

    private final PythonAiProxyService pythonAiProxyService;
    private final ObjectMapper objectMapper;

    public RagWeeklyJobRecommendationClient(PythonAiProxyService pythonAiProxyService, ObjectMapper objectMapper) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GeneratedJobRecommendation> generateRecommendations(String authorization) throws Exception {
        Object raw = pythonAiProxyService.queryWeeklyJobRecommendations(authorization);
        String answer = extractAnswer(raw);
        if (!StringUtils.hasText(answer)) {
            throw new IllegalStateException("岗位雷达智能体未返回可用内容");
        }

        JsonNode root = objectMapper.readTree(unwrapJsonBlock(answer));
        JsonNode items = root.isArray() ? root : root.path("jobs");
        if (!items.isArray()) {
            throw new IllegalStateException("岗位推荐 JSON 不是数组");
        }

        List<GeneratedJobRecommendation> result = new ArrayList<>();
        for (JsonNode item : items) {
            result.add(new GeneratedJobRecommendation(
                    item.path("jobTitle").asText(item.path("岗位").asText("")),
                    item.path("salary").asText(item.path("薪资").asText("")),
                    item.path("skills").asText(item.path("所需技能").asText(""))
            ));
        }
        return result;
    }

    private String extractAnswer(Object raw) {
        if (raw instanceof Map<?, ?> responseMap) {
            Object answer = responseMap.get("answer");
            if (answer != null) {
                return String.valueOf(answer);
            }
        }
        if (raw instanceof String answer) {
            return answer;
        }
        return "";
    }

    private String unwrapJsonBlock(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > -1 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
