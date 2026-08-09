package com.example.appbackend.service.impl;

import com.example.appbackend.entity.SystemConfig;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.FileSummaryResult;
import com.example.appbackend.service.FileSummaryService;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class FileSummaryServiceImpl implements FileSummaryService {
    private static final Logger log = LoggerFactory.getLogger(FileSummaryServiceImpl.class);
    private static final String AGENT_MODEL_BINDING_PREFIX = "ai.agent-bindings.";
    private static final String FILE_SUMMARY_AGENT_NAME = "file_summary_agent";
    private static final String DEFAULT_AGENT_NAME = "leader_agent";
    private static final String LEGACY_TEXT_CONFIG_PREFIX = "ai.service.text";
    private static final int MAX_SUMMARY_SOURCE_LENGTH = 12_000;
    private static final int MAX_SUMMARY_LENGTH = 260;

    private final SystemConfigService systemConfigService;
    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    public FileSummaryServiceImpl(SystemConfigService systemConfigService,
                                  SystemConfigRepository systemConfigRepository,
                                  ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public FileSummaryResult summarize(String fileName, String text) {
        String content = trim(text);
        if (!StringUtils.hasText(content)) {
            return new FileSummaryResult("", "EMPTY", "local");
        }

        AiRuntimeConfig config = resolveRuntimeConfig();
        if (config != null) {
            try {
                String summary = requestAiSummary(config, fileName, content);
                if (StringUtils.hasText(summary)) {
                    return new FileSummaryResult(cleanSummary(summary), "AI", config.model());
                }
            } catch (Exception error) {
                log.warn("文件 AI 总结失败，使用本地摘要 fileName={}", fileName, error);
                return localSummary(fileName, content, "AI_FALLBACK");
            }
        }
        return localSummary(fileName, content, "LOCAL");
    }

    private String requestAiSummary(AiRuntimeConfig config, String fileName, String content) throws Exception {
        Map<String, Object> payload = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "你是文档解析助手。请用中文概括上传文件，帮助后续 AI 生成思维导图、流程图或架构图。只输出摘要正文，不要 Markdown。"
                        ),
                        Map.of(
                                "role", "user",
                                "content", buildPrompt(fileName, content)
                        )
                ),
                "temperature", 0.2
        );

        String responseText = WebClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build()
                .post()
                .uri("/chat/completions")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(responseText);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String buildPrompt(String fileName, String content) {
        String source = content.length() > MAX_SUMMARY_SOURCE_LENGTH
                ? content.substring(0, MAX_SUMMARY_SOURCE_LENGTH)
                : content;
        return "文件名：" + firstText(fileName, "上传文件")
                + "\n\n请总结这个文件的主题、核心内容、关键对象/流程/模块，控制在120到220字。"
                + "\n如果内容适合生成图，请点明适合生成哪类结构。"
                + "\n\n文件解析内容：\n" + source;
    }

    private FileSummaryResult localSummary(String fileName, String content, String status) {
        String normalized = content
                .replaceAll("【第 \\d+ 页】", " ")
                .replaceAll("【第 \\d+ 张幻灯片】", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 180) {
            normalized = normalized.substring(0, 180).trim() + "...";
        }
        String summary = "已解析《" + firstText(fileName, "上传文件") + "》，可用于后续 AI 生成。主要内容包括：" + normalized;
        return new FileSummaryResult(cleanSummary(summary), status, "local");
    }

    private FileSummaryResult localSummary(String fileName, String content) {
        return localSummary(fileName, content, "LOCAL");
    }

    private String cleanSummary(String value) {
        String text = trim(value);
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?is)^```(?:markdown|text)?\\s*", "")
                    .replaceFirst("(?is)\\s*```$", "")
                    .trim();
        }
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > MAX_SUMMARY_LENGTH ? text.substring(0, MAX_SUMMARY_LENGTH).trim() + "..." : text;
    }

    private AiRuntimeConfig resolveRuntimeConfig() {
        String configPrefix = firstText(
                resolveAgentBoundModel(FILE_SUMMARY_AGENT_NAME),
                resolveAgentBoundModel(DEFAULT_AGENT_NAME),
                firstTestedTextConfigPrefix(),
                firstCompleteTextConfigPrefix(),
                hasCompleteConfig(LEGACY_TEXT_CONFIG_PREFIX) ? LEGACY_TEXT_CONFIG_PREFIX : ""
        );
        if (!StringUtils.hasText(configPrefix)) {
            return null;
        }
        return new AiRuntimeConfig(
                configPrefix,
                systemConfigService.getValue(configPrefix + ".api-key", "").trim(),
                trimTrailingSlash(systemConfigService.getValue(configPrefix + ".base-url", "").trim()),
                systemConfigService.getValue(configPrefix + ".model", "").trim()
        );
    }

    private String resolveAgentBoundModel(String agentName) {
        String value = systemConfigService.getValue(AGENT_MODEL_BINDING_PREFIX + agentName + ".model", "");
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private boolean hasCompleteConfig(String configPrefix) {
        return StringUtils.hasText(systemConfigService.getValue(configPrefix + ".api-key", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".base-url", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".model", ""));
    }

    private String firstTestedTextConfigPrefix() {
        List<SystemConfig> configs = systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1);
        if (configs == null) {
            return "";
        }
        return configs.stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".tested-fingerprint"))
                .filter(config -> StringUtils.hasText(config.getConfigValue()))
                .map(config -> removeSuffix(config.getConfigKey(), ".tested-fingerprint"))
                .filter(this::hasCompleteConfig)
                .sorted()
                .findFirst()
                .orElse("");
    }

    private String firstCompleteTextConfigPrefix() {
        List<SystemConfig> configs = systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1);
        if (configs == null) {
            return "";
        }
        return configs.stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".model"))
                .map(config -> removeSuffix(config.getConfigKey(), ".model"))
                .filter(this::hasCompleteConfig)
                .sorted()
                .findFirst()
                .orElse("");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String removeSuffix(String value, String suffix) {
        if (value == null || suffix == null || !value.endsWith(suffix)) {
            return "";
        }
        return value.substring(0, value.length() - suffix.length());
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record AiRuntimeConfig(String configPrefix, String apiKey, String baseUrl, String model) {
    }
}
