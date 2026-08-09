package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MindMapDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.MindMapAIService;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.service.support.MindMapTopicExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MindMapAIServiceImpl implements MindMapAIService {
    private static final int MAX_AI_INPUT_CHARS = 60_000;
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
    public MindMapDTO.MindMapData generate(String inputText, String centerTopic, String depth, String structure, String detail, String authorization) {
        String resolvedCenterTopic = MindMapTopicExtractor.extract(centerTopic, inputText, "", "");
        AiRuntimeConfig aiConfig = resolveRuntimeConfig();
        if (aiConfig == null) {
            return generateLocalMindMap(inputText, resolvedCenterTopic, depth, structure, detail);
        }
        String prompt = buildPrompt(inputText, resolvedCenterTopic, depth, structure, detail);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个专业知识结构化助手。你必须只返回严格 JSON，不要输出 Markdown、解释、代码块或多余文本。");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", aiConfig.model());
        payload.put("messages", List.of(systemMessage, userMessage));
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
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(500, "AI 未返回思维导图 JSON");
            }
            return parseAndValidate(content, resolvedCenterTopic, inputText);
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
        payload.put("messages", List.of(systemMessage, userMessage));
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
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
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

    private String buildPrompt(String inputText, String centerTopic, String depth, String structure, String detail) {
        String normalizedInput = inputText == null ? "" : inputText.trim();
        if (normalizedInput.length() > MAX_AI_INPUT_CHARS) {
            normalizedInput = normalizedInput.substring(0, MAX_AI_INPUT_CHARS);
        }
        String normalizedCenterTopic = defaultText(centerTopic, "");
        String normalizedStructure = normalizeStructure(structure);
        String structureInstruction = structureInstruction(normalizedStructure);
        return """
                请根据输入内容生成树形思维导图 JSON。

                要求：
                - 层级清晰
                - 不遗漏核心知识
                - 不生成无关内容
                - 如果“建议中心主题”不为空，JSON title 必须围绕它命名
                - 不要把“生成、制作、思维导图”等操作词、文件编号、日期或姓名当成 title
                - title 控制在 10 个汉字左右，优先表达学科、课程、项目或知识对象
                - 必须按照“结构方式规则”组织一级节点；不同结构方式的一级节点命名和组织维度必须明显不同，不要只替换同义词
                - 只返回严格 JSON，不要 Markdown，不要解释
                - JSON 顶层必须包含 title 和 nodes
                - nodes 是数组，每个节点包含 name，可选 children

                输出格式：
                {
                  "title": "计算机网络知识体系",
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

                层级深度：%s
                结构方式：%s
                结构方式规则：%s
                详细程度：%s
                建议中心主题：%s

                输入内容：
                %s
                """.formatted(defaultText(depth, "自动"), normalizedStructure, structureInstruction,
                defaultText(detail, "standard"), normalizedCenterTopic, normalizedInput);
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
                resolveAgentBoundModel(MIND_MAP_AGENT_NAME),
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
                requireAiConfig(configPrefix, "api-key", "AI Key"),
                trimTrailingSlash(requireAiConfig(configPrefix, "base-url", "AI 服务地址")),
                requireAiConfig(configPrefix, "model", "AI 模型 ID")
        );
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

    private String normalizeStructure(String structure) {
        String value = defaultText(structure, "自动").toLowerCase();
        if (value.contains("课程体系") || value.contains("course")) {
            return "课程体系";
        }
        if (value.contains("复习提纲") || value.contains("review") || value.contains("exam")) {
            return "复习提纲";
        }
        if (value.contains("项目拆解") || value.contains("project") || value.contains("task")) {
            return "项目拆解";
        }
        if (value.contains("知识梳理") || value.contains("knowledge")) {
            return "知识梳理";
        }
        if (value.contains("自动") || value.contains("auto")) {
            return "自动";
        }
        return "知识梳理";
    }

    private String structureInstruction(String structure) {
        return switch (structure) {
            case "课程体系" -> "按课程模块、先修关系、系统能力、实践训练和拓展方向组织一级节点，突出教学路径与模块层次。";
            case "复习提纲" -> "按考试重点、知识清单、易错难点、题型练习和复盘安排组织一级节点，突出重点回顾与复习路径。";
            case "项目拆解" -> "按项目目标、阶段计划、任务分解、资源依赖和交付验收组织一级节点，突出任务边界、依赖和执行顺序。";
            case "知识梳理" -> "按概念定义、核心原理、关键方法、应用场景和总结复盘组织一级节点，突出关键点提炼与知识关系。";
            default -> "先判断输入更适合课程体系、复习提纲、项目拆解还是知识梳理，再使用对应结构规则生成，不要混用多种组织方式。";
        };
    }

    private String inferStructure(String title) {
        String normalized = defaultText(title, "").toLowerCase();
        if (title.contains("复习") || title.contains("提纲") || title.contains("考试")
                || normalized.contains("review") || normalized.contains("exam")) {
            return "复习提纲";
        }
        if (title.contains("项目") || title.contains("任务") || title.contains("拆解")
                || normalized.contains("project") || normalized.contains("task")) {
            return "项目拆解";
        }
        if (title.contains("课程") || title.contains("体系") || title.contains("模块")
                || title.contains("计算机") || normalized.contains("course") || normalized.contains("computer")) {
            return "课程体系";
        }
        return "知识梳理";
    }

    private MindMapDTO.MindMapData generateLocalMindMap(String inputText, String centerTopic, String depth, String structure, String detail) {
        String title = normalizeTitle(inputText, centerTopic);
        int maxDepth = resolveDepth(depth);
        String normalizedStructure = normalizeStructure(structure);
        boolean detailed = "detailed".equalsIgnoreCase(defaultText(detail, "standard"))
                || "详细".equals(defaultText(detail, "standard"));

        MindMapDTO.MindMapData data = new MindMapDTO.MindMapData();
        data.setTitle(title);
        data.setNodes(buildTopicNodes(title, maxDepth, detailed, normalizedStructure));
        validateMindMap(data);
        return data;
    }

    private List<MindMapDTO.Node> buildTopicNodes(String title, int maxDepth, boolean detailed, String structure) {
        String resolvedStructure = "自动".equals(structure) ? inferStructure(title) : structure;
        return buildNodes(maxDepth, detailed, switch (resolvedStructure) {
            case "课程体系" -> courseBranches(title);
            case "复习提纲" -> reviewBranches();
            case "项目拆解" -> projectBranches();
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
                branch("考试重点", "高频概念", "核心公式", "必背结论"),
                branch("知识清单", "章节要点", "关联关系", "记忆线索"),
                branch("易错难点", "常见误区", "辨析方法", "纠错提醒"),
                branch("题型练习", "基础题型", "综合题型", "答题步骤"),
                branch("复盘安排", "错题整理", "阶段检查", "临考回顾")
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

    private int resolveDepth(String depth) {
        String value = defaultText(depth, "3");
        if (value.contains("2")) {
            return 2;
        }
        if (value.contains("4")) {
            return 4;
        }
        return 3;
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
