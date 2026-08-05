package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FlowchartDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.FlowchartAIService;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FlowchartAIServiceImpl implements FlowchartAIService {
    private static final int MAX_AI_INPUT_CHARS = 60_000;
    private static final String FLOWCHART_AGENT_NAME = "diagram_flowchart_agent";
    private static final String DEFAULT_AGENT_NAME = "leader_agent";
    private static final String AGENT_MODEL_BINDING_PREFIX = "ai.agent-bindings.";

    private final SystemConfigService systemConfigService;
    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    public FlowchartAIServiceImpl(SystemConfigService systemConfigService,
                                  SystemConfigRepository systemConfigRepository,
                                  ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public FlowchartDTO.FlowchartData generate(FlowchartDTO.GenerateRequest request,
                                                String inputText,
                                                String authorization) {
        String configPrefix = resolveTextConfigPrefix();
        if (!StringUtils.hasText(configPrefix)) {
            throw new BusinessException(400, "AI 文本模型未配置，请在系统配置中维护 ai.service.text.* 或 ai.agent-bindings." + DEFAULT_AGENT_NAME + ".model");
        }
        String apiKey = requireAiConfig(configPrefix, "api-key", "AI Key");
        String baseUrl = trimTrailingSlash(requireAiConfig(configPrefix, "base-url", "AI 服务地址"));
        String model = requireAiConfig(configPrefix, "model", "AI 模型 ID");

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一名专业流程设计师。你必须只返回严格 JSON，不要输出 Markdown、解释、代码块或多余文本。");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", buildPrompt(request, inputText));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(systemMessage, userMessage));
        payload.put("temperature", 0.2);

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
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(500, "AI 未返回流程图 JSON");
            }
            return parseAndValidate(content);
        } catch (WebClientResponseException error) {
            throw new BusinessException(500, "AI 请求失败: " + error.getResponseBodyAsString());
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 流程图生成失败: " + error.getMessage());
        }
    }

    private String buildPrompt(FlowchartDTO.GenerateRequest request, String inputText) {
        String normalizedInput = inputText == null ? "" : inputText.trim();
        if (normalizedInput.length() > MAX_AI_INPUT_CHARS) {
            normalizedInput = normalizedInput.substring(0, MAX_AI_INPUT_CHARS);
        }
        return """
                你是一名专业流程设计师。请根据用户需求生成标准流程图。

                要求：
                - 步骤清晰，使用 action、decision、start、end、data、exception 等节点类型
                - 判断节点必须明确 condition，并用 edges 的 label 或 condition 标记分支
                - 当需要角色泳道时，lanes 中给出角色名称，节点的 lane 填对应角色
                - 根据展示内容补充节点 input、output、exception 字段
                - 返回严格 JSON，不要 Markdown，不要解释
                - 每个节点 id 唯一；每条 edge 的 source 和 target 必须引用节点 id

                输出格式：
                {
                  "title": "商品发布流程",
                  "type": "SWIMLANE",
                  "lanes": [{"name": "用户", "nodes": ["1"]}],
                  "nodes": [
                    {"id": "1", "name": "提交商品信息", "type": "action", "lane": "用户"},
                    {"id": "2", "name": "审核是否通过", "type": "decision", "condition": "是否符合规范"}
                  ],
                  "edges": [
                    {"source": "1", "target": "2"}
                  ]
                }

                流程类型：%s
                表达方式：%s
                节点粒度：%s
                判断节点：%s
                角色泳道：%s
                展示内容：%s

                用户输入：
                %s
                """.formatted(
                defaultText(request.getProcessType(), "BUSINESS"),
                defaultText(request.getDiagramType(), "AUTO"),
                defaultText(request.getNodeLevel(), "AUTO"),
                defaultText(request.getDecisionMode(), "AUTO"),
                defaultText(request.getSwimlane(), "AUTO"),
                request.getDisplayItems() == null || request.getDisplayItems().isEmpty()
                        ? "STEP" : String.join(", ", request.getDisplayItems()),
                normalizedInput
        );
    }

    private FlowchartDTO.FlowchartData parseAndValidate(String content) {
        try {
            FlowchartDTO.FlowchartData data = objectMapper.readValue(extractJson(content), FlowchartDTO.FlowchartData.class);
            validate(data);
            return data;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 返回内容不是合法流程图 JSON");
        }
    }

    private void validate(FlowchartDTO.FlowchartData data) {
        if (data == null || !StringUtils.hasText(data.getTitle())) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 title");
        }
        if (data.getNodes() == null || data.getNodes().isEmpty()) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 nodes");
        }
        if (data.getEdges() == null) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 edges");
        }

        Set<String> nodeIds = new HashSet<>();
        for (FlowchartDTO.Node node : data.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getId()) || !StringUtils.hasText(node.getName())) {
                throw new BusinessException(500, "AI 返回 JSON 存在无效节点");
            }
            if (!nodeIds.add(node.getId())) {
                throw new BusinessException(500, "AI 返回 JSON 存在重复节点 ID");
            }
            if (!StringUtils.hasText(node.getType())) {
                node.setType("action");
            }
        }
        for (FlowchartDTO.Edge edge : data.getEdges()) {
            if (edge == null || !nodeIds.contains(edge.getSource()) || !nodeIds.contains(edge.getTarget())) {
                throw new BusinessException(500, "AI 返回 JSON 存在无效连接");
            }
        }
        if (data.getLanes() == null) {
            data.setLanes(List.of());
        }
        if (!StringUtils.hasText(data.getType())) {
            data.setType("FLOWCHART");
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

    private String requireAiConfig(String configPrefix, String field, String label) {
        String key = configPrefix + "." + field;
        String value = systemConfigService.getValue(key, "");
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, label + " 未配置，请在系统配置中维护 " + key);
        }
        return value;
    }

    /**
     * 解析当前流程图可用的文本模型配置前缀。
     * 顺序：流程图专属绑定 → leader_agent 绑定 → 已测试通过的 text 配置 → 任意完整 text 配置 → 兜底 ai.service.text。
     */
    private String resolveTextConfigPrefix() {
        String bound = firstText(
                resolveAgentBoundModel(FLOWCHART_AGENT_NAME),
                resolveAgentBoundModel(DEFAULT_AGENT_NAME),
                firstTestedTextConfigPrefix(),
                firstCompleteTextConfigPrefix()
        );
        if (StringUtils.hasText(bound)) {
            return bound;
        }
        // 兜底：老格式 ai.service.text.{field}，仅当 api-key/base-url/model 都有时可用
        return hasCompleteConfig("ai.service.text") ? "ai.service.text" : "";
    }

    private String resolveAgentBoundModel(String agentName) {
        String key = AGENT_MODEL_BINDING_PREFIX + agentName + ".model";
        String value = systemConfigService.getValue(key, "");
        return StringUtils.hasText(value) ? value.trim() : "";
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

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
