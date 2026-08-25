package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AiWriteDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.AiService;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService {

    private final SystemConfigService systemConfigService;
    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    public AiServiceImpl(SystemConfigService systemConfigService,
                         SystemConfigRepository systemConfigRepository,
                         ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiWriteDTO.WriteResponse write(AiWriteDTO.WriteRequest request) {
        String configPrefix = resolveTextConfigPrefix(request);
        String provider = requireAiConfig(configPrefix, "provider", "模型服务商");
        String apiKey = requireAiConfig(configPrefix, "api-key", "AI Key");
        String baseUrl = trimTrailingSlash(requireAiConfig(configPrefix, "base-url", "AI 服务地址"));
        String configuredModel = requireAiConfig(configPrefix, "model", "AI 模型 ID");
        String model = AiModelPolicy.effectiveFreeTextModel(provider, configuredModel);
        if (!StringUtils.hasText(model)) {
            throw new BusinessException(400,
                    "当前文本模型不在免费额度清单内，请改用 " + AiModelPolicy.defaultTextModel());
        }
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
            throw new BusinessException(500, "AI 请求失败: " + formatAiError(detail));
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 写作失败: " + error.getMessage());
        }
    }

    @Override
    public List<AiWriteDTO.ModelOption> listAvailableTextModels() {
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .forEach(config -> {
                    String key = config.getConfigKey();
                    String field = extractTextModelConfigField(key);
                    if (!StringUtils.hasText(field)) {
                        return;
                    }
                    String prefix = removeSuffix(key, "." + field);
                    grouped.computeIfAbsent(prefix, ignored -> new HashMap<>())
                            .put(field, config.getConfigValue() == null ? "" : config.getConfigValue().trim());
                });

        List<AiWriteDTO.ModelOption> options = new ArrayList<>();
        grouped.forEach((prefix, fields) -> {
            String provider = fields.getOrDefault("provider", "");
            String baseUrl = fields.getOrDefault("base-url", "");
            String apiKey = fields.getOrDefault("api-key", "");
            String configuredModel = fields.getOrDefault("model", "");
            String model = AiModelPolicy.effectiveFreeTextModel(provider, configuredModel);
            String testedFingerprint = fields.getOrDefault("tested-fingerprint", "");
            boolean tested = StringUtils.hasText(testedFingerprint);
            if (!StringUtils.hasText(provider) || !StringUtils.hasText(baseUrl)
                    || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)
                    || !tested || !AiModelPolicy.isFreeTextModel(model)) {
                return;
            }
            String providerName = providerDisplayName(provider, prefix, model);
            AiWriteDTO.ModelOption option = new AiWriteDTO.ModelOption();
            option.setConfigPrefix(prefix);
            option.setProvider(provider);
            option.setProviderName(providerName);
            option.setModel(model);
            option.setDisplayName(providerName + " · " + model);
            option.setTested(true);
            options.add(option);
        });
        options.sort(Comparator.comparing(AiWriteDTO.ModelOption::getProviderName)
                .thenComparing(AiWriteDTO.ModelOption::getModel));
        return options;
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
        return value.replaceAll("/+$", "");
    }

    private String formatAiError(String detail) {
        if (!StringUtils.hasText(detail)) {
            return "模型服务未返回错误详情";
        }
        String message = detail;
        String code = "";
        try {
            JsonNode root = objectMapper.readTree(detail);
            JsonNode error = root.path("error");
            message = error.path("message").asText(message);
            code = error.path("code").asText("");
        } catch (Exception ignored) {
        }
        String normalized = (message + " " + code).toLowerCase();
        if (normalized.contains("authentication") || normalized.contains("invalid api key")) {
            return "AI Key 验证失败，请检查后台模型配置中的 API Key 是否正确、是否可用";
        }
        if (normalized.contains("insufficient") || normalized.contains("quota") || normalized.contains("balance")) {
            return "模型额度不足，请检查服务商账号余额或额度";
        }
        if (normalized.contains("model")) {
            return "模型配置不可用，请检查后台模型 ID 和服务地址";
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    private String requireAiConfig(String configPrefix, String field, String label) {
        String key = configPrefix + "." + field;
        String value = systemConfigService.getValue(key, "");
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, label + " 未配置，请在系统配置中维护 " + key);
        }
        return value;
    }

    private String resolveTextConfigPrefix(AiWriteDTO.WriteRequest request) {
        String requested = normalize(request == null ? "" : request.getModelName());
        String matched = firstMatchingTextConfigPrefix(requested, true);
        if (StringUtils.hasText(matched)) {
            return matched;
        }
        matched = firstMatchingTextConfigPrefix(requested, false);
        if (StringUtils.hasText(matched)) {
            return matched;
        }
        matched = firstTestedTextConfigPrefix();
        if (StringUtils.hasText(matched)) {
            return matched;
        }
        matched = firstCompleteTextConfigPrefix();
        if (StringUtils.hasText(matched)) {
            return matched;
        }
        return hasCompleteConfig("ai.service.text") ? "ai.service.text" : "ai.service.text";
    }

    private String firstMatchingTextConfigPrefix(String requested, boolean testedOnly) {
        if (!StringUtils.hasText(requested)) {
            return "";
        }
        return systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".model"))
                .map(config -> removeSuffix(config.getConfigKey(), ".model"))
                .filter(this::hasCompleteConfig)
                .filter(this::isFreeTextConfig)
                .filter(prefix -> !testedOnly || StringUtils.hasText(systemConfigService.getValue(prefix + ".tested-fingerprint", "")))
                .filter(prefix -> textConfigMatches(prefix, requested))
                .distinct()
                .sorted(Comparator.comparingInt(this::textConfigPriority).thenComparing(String::toString))
                .findFirst()
                .orElse("");
    }

    private boolean textConfigMatches(String configPrefix, String requested) {
        return normalize(configPrefix).contains(requested)
                || normalize(systemConfigService.getValue(configPrefix + ".provider", "")).contains(requested)
                || normalize(systemConfigService.getValue(configPrefix + ".model", "")).contains(requested);
    }

    private String firstTestedTextConfigPrefix() {
        return systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".tested-fingerprint"))
                .filter(config -> StringUtils.hasText(config.getConfigValue()))
                .map(config -> removeSuffix(config.getConfigKey(), ".tested-fingerprint"))
                .filter(this::hasCompleteConfig)
                .filter(this::isFreeTextConfig)
                .sorted(Comparator.comparingInt(this::textConfigPriority).thenComparing(String::toString))
                .findFirst()
                .orElse("");
    }

    private String firstCompleteTextConfigPrefix() {
        return systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".model"))
                .map(config -> removeSuffix(config.getConfigKey(), ".model"))
                .filter(this::hasCompleteConfig)
                .filter(this::isFreeTextConfig)
                .distinct()
                .sorted(Comparator.comparingInt(this::textConfigPriority).thenComparing(String::toString))
                .findFirst()
                .orElse("");
    }

    private boolean isFreeTextConfig(String configPrefix) {
        return StringUtils.hasText(AiModelPolicy.effectiveFreeTextModel(
                systemConfigService.getValue(configPrefix + ".provider", ""),
                systemConfigService.getValue(configPrefix + ".model", "")
        ));
    }

    private int textConfigPriority(String configPrefix) {
        return AiModelPolicy.priority(AiModelPolicy.effectiveFreeTextModel(
                systemConfigService.getValue(configPrefix + ".provider", ""),
                systemConfigService.getValue(configPrefix + ".model", "")
        ));
    }

    private boolean hasCompleteConfig(String configPrefix) {
        return StringUtils.hasText(systemConfigService.getValue(configPrefix + ".api-key", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".base-url", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".model", ""));
    }

    private String removeSuffix(String value, String suffix) {
        if (value == null || suffix == null || !value.endsWith(suffix)) {
            return "";
        }
        return value.substring(0, value.length() - suffix.length());
    }

    private String extractTextModelConfigField(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        for (String field : List.of("provider", "base-url", "api-key", "model", "tested-fingerprint")) {
            if (key.endsWith("." + field)) {
                return field;
            }
        }
        return "";
    }

    private String providerDisplayName(String provider, String prefix, String model) {
        String normalized = normalize(provider + " " + prefix + " " + model);
        if (normalized.contains("deepseek")) {
            return "DeepSeek";
        }
        if (normalized.contains("doubao") || normalized.contains("volc") || normalized.contains("bytedance")) {
            return "豆包";
        }
        if (normalized.contains("tongyi") || normalized.contains("qwen") || normalized.contains("dashscope") || normalized.contains("aliyun")) {
            return "通义千问";
        }
        if (StringUtils.hasText(provider)) {
            return provider.trim();
        }
        return "文本模型";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
    }
}
