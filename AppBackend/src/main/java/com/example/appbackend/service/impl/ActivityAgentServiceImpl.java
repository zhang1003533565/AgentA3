package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ActivityAgentGenerateRequest;
import com.example.appbackend.dto.ActivityAgentGenerateResponse;
import com.example.appbackend.dto.CategoryResponse;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ActivitiyCategoryService;
import com.example.appbackend.service.ActivityAgentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ActivityAgentServiceImpl implements ActivityAgentService {

    private static final String AGENT_NAME = "activity_publish_agent";
    private static final List<String> ACTIVITY_FIELDS = List.of(
            "title", "organizerName", "coverImage", "categoryId", "maxPeople",
            "location", "startTime", "endTime", "signupEndTime", "content");
    private static final Set<String> ACTIVITY_FIELD_SET = Set.copyOf(ACTIVITY_FIELDS);
    private static final Set<String> AI_GENERATABLE_FIELDS = Set.of("title", "content");
    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "action", "reply", "activity", "generatedFields", "missingFields",
            "confidentFields", "warnings");

    private final PythonAiProxyService pythonAiProxyService;
    private final ActivitiyCategoryService activityCategoryService;
    private final ObjectMapper objectMapper;

    public ActivityAgentServiceImpl(PythonAiProxyService pythonAiProxyService,
                                    ActivitiyCategoryService activityCategoryService,
                                    ObjectMapper objectMapper) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.activityCategoryService = activityCategoryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ActivityAgentGenerateResponse generate(ActivityAgentGenerateRequest request, String authorization) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("userInput", request.getInput());
        contract.put("activityDraft", normalizeDraft(request.getActivityDraft()));
        contract.put("generatedFields", normalizeGeneratedFields(request.getGeneratedFields()));
        contract.put("categoryOptions", buildCategoryOptions());
        contract.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        contract.put("conversationContext",
                request.getConversationContext() == null ? Map.of() : request.getConversationContext());

        Map<String, Object> proxyRequest = new LinkedHashMap<>();
        proxyRequest.put("agentName", AGENT_NAME);
        proxyRequest.put("input", writeJson(contract));
        if (StringUtils.hasText(request.getLlmModel())) {
            proxyRequest.put("llmModel", request.getLlmModel().trim());
        }

        Object raw = pythonAiProxyService.queryRag(proxyRequest, authorization);
        Map<String, Object> ragResult = asMap(raw, "Python AI 服务返回结果格式异常");
        Object answer = ragResult.get("answer");
        if (!(answer instanceof String) || !StringUtils.hasText((String) answer)) {
            throw new BusinessException(502, "AI 未返回活动草稿内容");
        }
        String answerText = ((String) answer).trim();
        return buildResponse(parseAnswer(answerText), answerText, ragResult);
    }

    private ActivityAgentGenerateResponse buildResponse(Map<String, Object> aiPayload,
                                                        String rawAnswer,
                                                        Map<String, Object> ragResult) {
        ActivityAgentGenerateResponse response = new ActivityAgentGenerateResponse();
        response.setAction(stringValue(aiPayload.get("action")));
        response.setReply(stringValue(aiPayload.get("reply")));
        response.setActivity(normalizeAiActivity(aiPayload.get("activity")));
        response.setGeneratedFields(toStringList(aiPayload.get("generatedFields")));
        response.setMissingFields(toStringList(aiPayload.get("missingFields")));
        response.setConfidentFields(toStringList(aiPayload.get("confidentFields")));
        response.setWarnings(toStringList(aiPayload.get("warnings")));
        response.setModel(resolveModel(ragResult));
        response.setAgentName(AGENT_NAME);
        response.setRawAnswer(rawAnswer);
        if (!StringUtils.hasText(response.getAction()) || !StringUtils.hasText(response.getReply())) {
            throw new BusinessException(502, "AI 返回的活动草稿缺少 action 或 reply");
        }
        return response;
    }

    private Map<String, Object> parseAnswer(String answerText) {
        try {
            Object parsed = objectMapper.readValue(answerText, Object.class);
            Map<String, Object> payload = asMap(parsed, "AI 返回内容不是 JSON 对象");
            Map<String, Object> filtered = new LinkedHashMap<>();
            payload.forEach((key, value) -> {
                if (TOP_LEVEL_KEYS.contains(key)) {
                    filtered.put(key, value);
                }
            });
            return filtered;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(502, "AI 返回的活动草稿不是合法 JSON");
        }
    }

    private Map<String, Object> normalizeAiActivity(Object value) {
        Map<String, Object> activity = asMap(value, "AI 返回缺少 activity 对象");
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String field : ACTIVITY_FIELDS) {
            normalized.put(field, activity.get(field));
        }
        return normalized;
    }

    private Map<String, Object> normalizeDraft(Map<String, Object> draft) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (draft == null) {
            for (String field : ACTIVITY_FIELDS) {
                normalized.put(field, null);
            }
            return normalized;
        }
        for (String field : ACTIVITY_FIELDS) {
            normalized.put(field, draft.get(field));
        }
        return normalized;
    }

    private List<String> normalizeGeneratedFields(List<String> generatedFields) {
        if (generatedFields == null || generatedFields.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String field : generatedFields) {
            if (!AI_GENERATABLE_FIELDS.contains(field)) {
                throw new BusinessException(400, "generatedFields 只能是 title/content");
            }
            if (seen.add(field)) {
                normalized.add(field);
            }
        }
        return normalized;
    }

    private List<Map<String, Object>> buildCategoryOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        for (CategoryResponse category : activityCategoryService.getAllCategories()) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", category.getId());
            option.put("name", category.getCategoryName());
            options.add(option);
        }
        return options;
    }

    private String resolveModel(Map<String, Object> ragResult) {
        Map<String, Object> metadata = asMap(ragResult.get("metadata"), null);
        Object model = metadata.get("model");
        return model == null ? null : String.valueOf(model);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String errorMessage) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (errorMessage != null) {
            throw new BusinessException(502, errorMessage);
        }
        return Map.of();
    }

    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 请求参数构造失败");
        }
    }
}
