package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MindMapDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.MindMapAIService;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.service.support.MindMapGenerationConstraints;
import com.example.appbackend.service.support.MindMapTopicExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MindMapAIServiceImpl implements MindMapAIService {
    private static final Logger log = LoggerFactory.getLogger(MindMapAIServiceImpl.class);
    private static final int MAX_AI_INPUT_CHARS = 60_000;
    private static final int MAX_RESPONSE_LOG_CHARS = 4_000;
    private static final String MIND_MAP_AGENT_NAME = "diagram_mind_map_agent";
    private static final String DEFAULT_AGENT_NAME = "leader_agent";
    private static final String AGENT_MODEL_BINDING_PREFIX = "ai.agent-bindings.";
    private static final String LEGACY_TEXT_CONFIG_PREFIX = "ai.service.text";

    private final SystemConfigService systemConfigService;
    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    public MindMapAIServiceImpl(SystemConfigService systemConfigService,
                                SystemConfigRepository systemConfigRepository,
                                ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public MindMapDTO.MindMapData generate(String inputText,
                                           String centerTopic,
                                           String centerTopicMode,
                                           String depth,
                                           String structure,
                                           String detail,
                                           String authorization) {
        MindMapGenerationConstraints constraints = MindMapGenerationConstraints.resolve(
                centerTopicMode,
                centerTopic,
                depth,
                structure,
                detail,
                inputText
        );
        AiRuntimeConfig aiConfig = resolveRuntimeConfig();
        if (aiConfig == null) {
            return generateLocalMindMap(inputText, constraints);
        }
        String prompt = buildPrompt(inputText, constraints);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个专业知识结构化助手。你必须只返回严格 JSON，不要输出 Markdown、解释、代码块或多余文本。");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", aiConfig.model());
        payload.put("messages", textMessages(prompt));
        payload.put("temperature", 0.2);

        try {
            String responseText = WebClient.builder()
                    .baseUrl(aiConfig.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + aiConfig.apiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseText);
            String content = extractResponseContent(root);
            if (!StringUtils.hasText(content)) {
                log.warn("AI mind-map response contains no usable text: {}", abbreviateResponse(responseText));
                throw new BusinessException(500, "AI 未返回思维导图 JSON");
            }
            return parseAndValidate(content, constraints, inputText);
        } catch (WebClientResponseException error) {
            throw new BusinessException(500, "AI 请求失败: " + error.getResponseBodyAsString());
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 思维导图生成失败: " + error.getMessage());
        }
    }

    @Override
    public MindMapDTO.MindMapData optimize(MindMapDTO.MindMapData currentMindMap, String userInstruction, String authorization) {
        AiRuntimeConfig aiConfig = resolveRuntimeConfig();
        if (aiConfig == null) {
            return optimizeLocal(currentMindMap, userInstruction);
        }

        String currentJson;
        try {
            currentJson = objectMapper.writeValueAsString(currentMindMap);
        } catch (Exception e) {
            throw new BusinessException(500, "当前思维导图序列化失败");
        }

        String prompt = """
                你是一个思维导图优化助手。用户已有一份思维导图，请根据用户的优化要求进行调整。

                要求：
                - 保留原有合理结构，仅根据用户要求做增量调整
                - 层级清晰，不遗漏核心知识
                - 只返回严格 JSON，不要 Markdown，不要解释
                - JSON 顶层必须包含 title 和 nodes
                - nodes 是数组，每个节点包含 name，可选 children

                输出格式：
                {
                  "title": "优化后的标题",
                  "nodes": [
                    {
                      "name": "节点名",
                      "children": [
                        { "name": "子节点" }
                      ]
                    }
                  ]
                }

                用户优化要求：
                %s

                当前思维导图 JSON：
                %s
                """.formatted(userInstruction, currentJson);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个思维导图优化助手。你必须只返回严格 JSON，不要输出 Markdown、解释、代码块或多余文本。");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", aiConfig.model());
        payload.put("messages", textMessages(prompt));
        payload.put("temperature", 0.3);

        try {
            String responseText = WebClient.builder()
                    .baseUrl(aiConfig.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + aiConfig.apiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseText);
            String content = extractResponseContent(root);
            if (!StringUtils.hasText(content)) {
                log.warn("AI mind-map optimization response contains no usable text: {}", abbreviateResponse(responseText));
                throw new BusinessException(500, "AI 未返回优化后的思维导图 JSON");
            }
            return parseAndValidate(content, currentMindMap == null ? "" : currentMindMap.getTitle(), userInstruction);
        } catch (WebClientResponseException error) {
            throw new BusinessException(500, "AI 请求失败: " + error.getResponseBodyAsString());
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 思维导图优化失败: " + error.getMessage());
        }
    }

    private MindMapDTO.MindMapData optimizeLocal(MindMapDTO.MindMapData current, String instruction) {
        // 本地兜底：返回当前数据，标题追加优化标记
        MindMapDTO.MindMapData result = new MindMapDTO.MindMapData();
        result.setTitle(current.getTitle() + "（优化版）");
        result.setNodes(current.getNodes());
        return result;
    }

    private String buildPrompt(String inputText, MindMapGenerationConstraints constraints) {
        String normalizedInput = inputText == null ? "" : inputText.trim();
        if (normalizedInput.length() > MAX_AI_INPUT_CHARS) {
            normalizedInput = normalizedInput.substring(0, MAX_AI_INPUT_CHARS);
        }
        return """
                请根据输入内容生成树形思维导图 JSON。

                %s

                输出要求：
                - 只返回严格 JSON，不要 Markdown，不要解释
                - JSON 顶层必须包含 title、requestedCenterTopicMode、resolvedCenterTopic、requestedDepth、resolvedDepth、requestedStructure、resolvedStructure、detailLevel 和 nodes
                - requestedDepth 必须为 "%s"，resolvedDepth 必须为最终采用的 2/3/4 数字
                - requestedStructure 必须为 "%s"，resolvedStructure 必须为最终采用的 KNOWLEDGE/COURSE/REVIEW/PROJECT
                - detailLevel 必须为 "%s"
                - nodes 是数组，每个节点包含 name，可选 children
                - title 与 resolvedCenterTopic 必须一致或语义完全一致

                输出格式：
                {
                  "title": "计算机网络",
                  "requestedCenterTopicMode": "%s",
                  "resolvedCenterTopic": "计算机网络",
                  "requestedDepth": "%s",
                  "resolvedDepth": 3,
                  "requestedStructure": "%s",
                  "resolvedStructure": "KNOWLEDGE",
                  "detailLevel": "%s",
                  "nodes": [
                    {
                      "name": "计算机网络基础",
                      "children": [
                        {
                          "name": "OSI模型",
                          "children": [
                            { "name": "七层结构" }
                          ]
                        }
                      ]
                    }
                  ]
                }

                当前系统预解析参数：
                requestedCenterTopicMode=%s
                resolvedCenterTopic=%s
                requestedDepth=%s
                resolvedDepth=%d
                requestedStructure=%s
                resolvedStructure=%s
                detailLevel=%s

                输入内容：
                %s
                """.formatted(
                constraints.promptInstructions(),
                constraints.requestedDepth(),
                constraints.requestedStructure(),
                constraints.detailLevel(),
                constraints.requestedCenterTopicMode(),
                constraints.requestedDepth(),
                constraints.requestedStructure(),
                constraints.detailLevel(),
                constraints.requestedCenterTopicMode(),
                constraints.resolvedCenterTopic(),
                constraints.requestedDepth(),
                constraints.resolvedDepth(),
                constraints.requestedStructure(),
                constraints.resolvedStructure(),
                constraints.detailLevel(),
                normalizedInput
        );
    }

    /**
     * The configured AI gateway accepts only user messages whose content is a
     * list of parts. Keep the output rule in the same user message.
     */
    private List<Map<String, Object>> textMessages(String prompt) {
        String instructions = "你是专业的知识结构化助手。你必须只返回严格 JSON，"
                + "不要输出 Markdown、解释、代码块或多余文本。";
        Map<String, Object> textPart = Map.of(
                "type", "text",
                "text", instructions + "\n\n" + prompt
        );
        return List.of(Map.of(
                "role", "user",
                "content", List.of(textPart)
        ));
    }

    private String extractResponseContent(JsonNode root) {
        List<JsonNode> candidates = List.of(
                root.path("choices").path(0).path("message").path("content"),
                root.path("output").path("choices").path(0).path("message").path("content"),
                root.path("data").path("choices").path(0).path("message").path("content"),
                root.path("choices").path(0).path("text"),
                root.path("output").path("choices").path(0).path("text"),
                root.path("choices").path(0).path("message").path("reasoning_content"),
                root.path("output").path("choices").path(0).path("message").path("reasoning_content"),
                root.path("output_text"),
                root.path("output")
        );
        for (JsonNode candidate : candidates) {
            String text = contentText(candidate);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return findTextContent(root);
    }

    private String findTextContent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = findTextContent(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            return "";
        }
        if (!node.isObject()) {
            return "";
        }

        for (String field : List.of("output_text", "content", "text", "reasoning_content", "arguments")) {
            if (node.has(field)) {
                String text = contentText(node.path(field));
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }

        var fields = node.fields();
        while (fields.hasNext()) {
            JsonNode child = fields.next().getValue();
            if (child.isContainerNode()) {
                String text = findTextContent(child);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private String contentText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (!node.isArray()) {
            String text = contentText(node.path("text"));
            return StringUtils.hasText(text) ? text : contentText(node.path("content"));
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode part : node) {
            String text = part.isTextual() ? part.asText() : contentText(part.path("text"));
            if (!StringUtils.hasText(text)) {
                text = contentText(part.path("content"));
            }
            if (StringUtils.hasText(text)) {
                result.append(text);
            }
        }
        return result.toString();
    }

    private String abbreviateResponse(String responseText) {
        if (responseText == null) {
            return "<null>";
        }
        String normalized = responseText.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= MAX_RESPONSE_LOG_CHARS
                ? normalized
                : normalized.substring(0, MAX_RESPONSE_LOG_CHARS) + "...<truncated>";
    }

    private MindMapDTO.MindMapData parseAndValidate(String content, MindMapGenerationConstraints constraints, String inputText) {
        String json = extractJson(content);
        try {
            MindMapDTO.MindMapData data = objectMapper.readValue(json, MindMapDTO.MindMapData.class);
            applyConstraints(data, constraints, inputText);
            validateMindMap(data);
            return data;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 返回内容不是合法思维导图 JSON");
        }
    }

    private MindMapDTO.MindMapData parseAndValidate(String content, String centerTopic, String inputText) {
        String json = extractJson(content);
        try {
            MindMapDTO.MindMapData data = objectMapper.readValue(json, MindMapDTO.MindMapData.class);
            String fallbackTitle = MindMapTopicExtractor.extract(centerTopic, inputText, "", "");
            data.setTitle(MindMapTopicExtractor.normalizeGeneratedTitle(data.getTitle(), fallbackTitle));
            validateMindMap(data);
            return data;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 返回内容不是合法思维导图 JSON");
        }
    }

    private void applyConstraints(MindMapDTO.MindMapData data,
                                  MindMapGenerationConstraints constraints,
                                  String inputText) {
        String resolvedCenterTopic;
        if (constraints.isUserDefinedCenterTopic()) {
            resolvedCenterTopic = constraints.resolvedCenterTopic();
        } else {
            String aiTopic = firstText(data.getResolvedCenterTopic(), data.getTitle());
            String fallbackTopic = MindMapTopicExtractor.extract("", inputText, "", "");
            resolvedCenterTopic = MindMapTopicExtractor.normalizeGeneratedTitle(aiTopic, firstText(fallbackTopic, constraints.resolvedCenterTopic()));
        }
        if (!StringUtils.hasText(resolvedCenterTopic)) {
            resolvedCenterTopic = "思维导图";
        }

        int resolvedDepth = "AUTO".equals(constraints.requestedDepth())
                ? normalizeResolvedDepth(data.getResolvedDepth(), constraints.resolvedDepth())
                : constraints.resolvedDepth();
        String resolvedStructure = "AUTO".equals(constraints.requestedStructure())
                ? normalizeStructureCode(data.getResolvedStructure(), constraints.resolvedStructure())
                : constraints.resolvedStructure();

        data.setTitle(constraints.isUserDefinedCenterTopic()
                ? constraints.resolvedCenterTopic()
                : MindMapTopicExtractor.normalizeGeneratedTitle(data.getTitle(), resolvedCenterTopic));
        data.setResolvedCenterTopic(data.getTitle());
        data.setRequestedCenterTopicMode(constraints.requestedCenterTopicMode());
        data.setRequestedDepth(constraints.requestedDepth());
        data.setResolvedDepth(resolvedDepth);
        data.setRequestedStructure(constraints.requestedStructure());
        data.setResolvedStructure(resolvedStructure);
        data.setDetailLevel(constraints.detailLevel());
        data.setNodes(sanitizeNodes(data.getNodes(), 1, resolvedDepth, data.getTitle(), constraints.siblingLimit()));
        if (data.getNodes().isEmpty()) {
            data.setNodes(buildTopicNodes(data.getTitle(), resolvedDepth, constraints.detailLevel(), resolvedStructure));
        }
    }

    private int normalizeResolvedDepth(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(2, Math.min(4, value));
    }

    private String normalizeStructureCode(String value, String fallback) {
        String normalized = defaultText(value, fallback).toLowerCase(Locale.ROOT);
        if (normalized.contains("course") || normalized.contains("课程")) return "COURSE";
        if (normalized.contains("review") || normalized.contains("exam") || normalized.contains("复习")) return "REVIEW";
        if (normalized.contains("project") || normalized.contains("task") || normalized.contains("项目")) return "PROJECT";
        if (normalized.contains("knowledge") || normalized.contains("知识")) return "KNOWLEDGE";
        return StringUtils.hasText(fallback) ? fallback : "KNOWLEDGE";
    }

    private List<MindMapDTO.Node> sanitizeNodes(List<MindMapDTO.Node> nodes,
                                                int level,
                                                int maxDepth,
                                                String parentName,
                                                int siblingLimit) {
        if (nodes == null || nodes.isEmpty() || level > maxDepth) {
            return new ArrayList<>();
        }
        List<MindMapDTO.Node> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String parentKey = semanticKey(parentName);
        for (MindMapDTO.Node node : nodes) {
            if (node == null) {
                continue;
            }
            String name = cleanNodeName(node.getName());
            String key = semanticKey(name);
            if (!StringUtils.hasText(name) || !StringUtils.hasText(key) || key.equals(parentKey) || seen.contains(key)) {
                continue;
            }
            node.setName(name);
            seen.add(key);
            if (level >= maxDepth) {
                node.setChildren(new ArrayList<>());
            } else {
                node.setChildren(sanitizeNodes(node.getChildren(), level + 1, maxDepth, name, siblingLimit));
            }
            result.add(node);
            if (result.size() >= siblingLimit) {
                break;
            }
        }
        return result;
    }

    private String cleanNodeName(String value) {
        String text = defaultText(value, "").replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        text = text.replaceAll("^(关于|有关|这里主要介绍|主要介绍|需要注意的是|这里主要讲|主要讲)", "").trim();
        text = text.replaceAll("(相关知识|相关内容|基本介绍|概念介绍|知识介绍|的介绍|介绍)$", "").trim();
        return text.length() > 36 ? text.substring(0, 36) : text;
    }

    private String semanticKey(String value) {
        return cleanNodeName(value)
                .replaceAll("[\\s\\p{Punct}，。、“”‘’（）()【】《》：:；;·-]+", "")
                .replace("相关", "")
                .replace("基础", "")
                .replace("基本", "")
                .toLowerCase(Locale.ROOT);
    }

    private void validateMindMap(MindMapDTO.MindMapData data) {
        if (data == null || !StringUtils.hasText(data.getTitle())) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 title");
        }
        if (data.getNodes() == null || data.getNodes().isEmpty()) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 nodes");
        }
        data.getNodes().forEach(this::validateNode);
    }

    private void validateNode(MindMapDTO.Node node) {
        if (node == null || !StringUtils.hasText(node.getName())) {
            throw new BusinessException(500, "AI 返回 JSON 存在空节点");
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(this::validateNode);
        }
    }

    private String extractJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?is)^```(?:json)?\\s*", "").replaceFirst("(?is)\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(500, "AI 未返回 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private AiRuntimeConfig resolveRuntimeConfig() {
        String configPrefix = firstText(
                resolveAgentBoundTextModel(MIND_MAP_AGENT_NAME),
                resolveAgentBoundTextModel(DEFAULT_AGENT_NAME),
                firstTestedTextConfigPrefix(),
                firstCompleteTextConfigPrefix(),
                hasCompleteConfig(LEGACY_TEXT_CONFIG_PREFIX) ? LEGACY_TEXT_CONFIG_PREFIX : ""
        );
        if (!StringUtils.hasText(configPrefix)) {
            return null;
        }

        return new AiRuntimeConfig(
                configPrefix,
                requireAiConfig(configPrefix, "api-key", "AI Key"),
                trimTrailingSlash(requireAiConfig(configPrefix, "base-url", "AI 服务地址")),
                requireAiConfig(configPrefix, "model", "AI 模型 ID")
        );
    }

    private String resolveAgentBoundTextModel(String agentName) {
        String key = AGENT_MODEL_BINDING_PREFIX + agentName + ".model";
        String value = systemConfigService.getValue(key, "");
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String configPrefix = value.trim();
        if (!isTextConfigPrefix(configPrefix)) {
            log.warn("Ignoring non-text model binding for {}: {}", agentName, configPrefix);
            return "";
        }
        return configPrefix;
    }

    private boolean isTextConfigPrefix(String configPrefix) {
        return LEGACY_TEXT_CONFIG_PREFIX.equals(configPrefix)
                || configPrefix.startsWith(LEGACY_TEXT_CONFIG_PREFIX + ".");
    }

    private boolean hasCompleteConfig(String configPrefix) {
        return StringUtils.hasText(systemConfigService.getValue(configPrefix + ".api-key", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".base-url", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".model", ""));
    }

    private String firstTestedTextConfigPrefix() {
        return systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".tested-fingerprint"))
                .filter(config -> StringUtils.hasText(config.getConfigValue()))
                .map(config -> removeSuffix(config.getConfigKey(), ".tested-fingerprint"))
                .filter(this::hasCompleteConfig)
                .sorted()
                .findFirst()
                .orElse("");
    }

    private String firstCompleteTextConfigPrefix() {
        return systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".model"))
                .map(config -> removeSuffix(config.getConfigKey(), ".model"))
                .filter(this::hasCompleteConfig)
                .sorted()
                .findFirst()
                .orElse("");
    }

    private String requireAiConfig(String configPrefix, String field, String label) {
        String key = configPrefix + "." + field;
        String value = systemConfigService.getValue(key, "");
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, label + " 未配置，请在系统配置中维护 " + key);
        }
        return value;
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

    private MindMapDTO.MindMapData generateLocalMindMap(String inputText, MindMapGenerationConstraints constraints) {
        String title = constraints.isUserDefinedCenterTopic()
                ? constraints.resolvedCenterTopic()
                : normalizeTitle(inputText, constraints.resolvedCenterTopic());
        MindMapDTO.MindMapData data = new MindMapDTO.MindMapData();
        data.setTitle(title);
        data.setRequestedCenterTopicMode(constraints.requestedCenterTopicMode());
        data.setResolvedCenterTopic(title);
        data.setRequestedDepth(constraints.requestedDepth());
        data.setResolvedDepth(constraints.resolvedDepth());
        data.setRequestedStructure(constraints.requestedStructure());
        data.setResolvedStructure(constraints.resolvedStructure());
        data.setDetailLevel(constraints.detailLevel());
        data.setNodes(buildTopicNodes(title, constraints.resolvedDepth(), constraints.detailLevel(), constraints.resolvedStructure()));
        validateMindMap(data);
        return data;
    }

    private List<MindMapDTO.Node> buildTopicNodes(String title, int maxDepth, String detailLevel, String structure) {
        boolean detailed = "DETAILED".equals(detailLevel);
        return buildNodes(maxDepth, detailed, switch (normalizeStructureCode(structure, "KNOWLEDGE")) {
            case "COURSE" -> courseBranches(title);
            case "REVIEW" -> reviewBranches();
            case "PROJECT" -> projectBranches();
            default -> knowledgeBranches(title);
        });
    }

    private List<List<String>> knowledgeBranches(String title) {
        String normalized = title.toLowerCase();
        if (normalized.contains("linux")) {
            return List.of(
                    branch("Linux 基础", "发行版与内核", "文件系统层级", "用户与权限"),
                    branch("常用命令", "文件目录操作", "文本处理命令", "进程与服务查看"),
                    branch("系统管理", "软件包管理", "磁盘与存储", "日志排查"),
                    branch("Shell 脚本", "变量与参数", "条件与循环", "函数与任务自动化"),
                    branch("网络与安全", "网络配置", "SSH 远程管理", "防火墙与权限控制"),
                    branch("学习实践", "搭建实验环境", "完成命令练习", "整理问题清单")
            );
        }
        return List.of(
                branch("概念定义", "核心定义", "背景价值", "适用范围"),
                branch("核心原理", "主要关系", "关键机制", "基础规律"),
                branch("关键方法", "分析步骤", "操作流程", "检查反馈"),
                branch("应用场景", "典型案例", "实践任务", "效果评估"),
                branch("总结复盘", "重点回顾", "常见问题", "后续计划")
        );
    }

    private List<List<String>> courseBranches(String title) {
        String normalized = title.toLowerCase();
        if (title.contains("计算机") || normalized.contains("computer")) {
            return List.of(
                    branch("基础课程", "程序设计", "离散数学", "计算机导论"),
                    branch("核心课程", "数据结构", "操作系统", "计算机网络"),
                    branch("系统能力", "数据库系统", "编译原理", "计算机组成原理"),
                    branch("工程实践", "软件工程", "项目实训", "版本管理"),
                    branch("拓展方向", "人工智能", "云计算", "网络安全")
            );
        }
        return List.of(
                branch("入门基础", "基础概念", "预备知识", "学习目标"),
                branch("核心模块", "模块划分", "先修关系", "能力要求"),
                branch("进阶专题", "专题方向", "综合应用", "拓展阅读"),
                branch("实践训练", "实验安排", "课程项目", "成果检查"),
                branch("拓展方向", "延伸领域", "能力迁移", "后续课程")
        );
    }

    private List<List<String>> reviewBranches() {
        return List.of(
                branch("核心知识", "重点概念", "关键结论", "必要关系"),
                branch("知识清单", "章节要点", "关联关系", "回顾线索"),
                branch("易错难点", "常见误区", "辨析方法", "纠错提醒"),
                branch("练习回顾", "基础练习", "综合练习", "解题步骤"),
                branch("复盘安排", "错题整理", "阶段检查", "回顾计划")
        );
    }

    private List<List<String>> projectBranches() {
        return List.of(
                branch("项目目标", "背景问题", "目标范围", "成功标准"),
                branch("阶段计划", "启动准备", "关键里程碑", "时间安排"),
                branch("任务分解", "核心任务", "负责人分工", "优先级排序"),
                branch("资源依赖", "人员资源", "工具环境", "外部依赖"),
                branch("交付验收", "交付物清单", "验收标准", "复盘改进")
        );
    }

    private List<MindMapDTO.Node> buildNodes(int maxDepth, boolean detailed, List<List<String>> branches) {
        List<MindMapDTO.Node> nodes = new ArrayList<>();
        for (List<String> branch : branches) {
            MindMapDTO.Node root = node(branch.get(0));
            if (maxDepth >= 2) {
                List<MindMapDTO.Node> children = new ArrayList<>();
                for (int index = 1; index < branch.size(); index++) {
                    MindMapDTO.Node child = node(branch.get(index));
                    if (maxDepth >= 3) {
                        child.setChildren(buildLeafNodes(branch.get(index), detailed));
                    }
                    children.add(child);
                }
                root.setChildren(children);
            }
            nodes.add(root);
        }
        return nodes;
    }

    private List<MindMapDTO.Node> buildLeafNodes(String parentName, boolean detailed) {
        List<MindMapDTO.Node> leaves = new ArrayList<>();
        leaves.add(node("学习要点"));
        leaves.add(node("实践任务"));
        if (detailed) {
            leaves.add(node("常见问题"));
            leaves.add(node("复习检查"));
        }
        if (parentName.contains("权限")) {
            leaves = new ArrayList<>(List.of(node("权限模型"), node("安全边界"), node("操作示例")));
        } else if (parentName.contains("网络")) {
            leaves = new ArrayList<>(List.of(node("配置项"), node("诊断命令"), node("连通性验证")));
        } else if (parentName.contains("项目") || parentName.contains("实践")) {
            leaves = new ArrayList<>(List.of(node("任务拆解"), node("交付物"), node("复盘记录")));
        }
        return leaves;
    }

    private List<String> branch(String name, String first, String second, String third) {
        return List.of(name, first, second, third);
    }

    private MindMapDTO.Node node(String name) {
        MindMapDTO.Node node = new MindMapDTO.Node();
        node.setName(name);
        return node;
    }

    private String normalizeTitle(String inputText, String centerTopic) {
        String title = MindMapTopicExtractor.extract(centerTopic, inputText, "", "");
        if (!StringUtils.hasText(title)) {
            title = "思维导图";
        }
        return title.length() > 40 ? title.substring(0, 40) : title;
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record AiRuntimeConfig(String configPrefix, String apiKey, String baseUrl, String model) {
    }
}
