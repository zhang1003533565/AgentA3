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
        String configPrefix = requireTextConfigPrefix();
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
            return parseAndValidate(content, request);
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
        String sceneType = normalizeSceneType(firstText(request.getSceneType(), request.getProcessType()));
        String granularity = normalizeNodeGranularity(firstText(request.getNodeGranularity(), request.getNodeLevel()));
        String layoutDirection = normalizeLayoutDirection(request.getLayoutDirection());
        String decisionMode = normalizeDecisionMode(request.getDecisionMode());
        String swimlaneMode = normalizeSwimlaneMode(firstText(request.getSwimlaneMode(), request.getSwimlane()));
        return """
                你是一名专业流程设计师。请根据用户需求生成标准流程图。

                【最高优先级业务事实｜HARD / Highest】
                严格保留用户提供的业务流程、参与者、部门、条件、成功/失败、通过/拒绝、异常、返回和重试路径。
                UI 设置只能改变组织粒度和呈现方式，不能删除、篡改或凭空增加业务事实。

                【强度规则】
                - HARD：必须遵守，除非会导致流程逻辑无法成立。
                - HIGH：强规则，优先执行，但禁止虚构业务逻辑。
                - MEDIUM：明确偏好，优先遵守，可为流程完整性做少量调整。
                - SOFT：推荐倾向，可根据输入内容判断。
                - AUTO：由你分析用户内容后决策，并返回 resolvedXXX 字段。

                %s
                %s
                %s
                %s
                %s

                【输出硬约束】
                - 返回严格 JSON，不要 Markdown，不要解释。
                - 每个节点 id 唯一；每条 edge 的 source 和 target 必须引用节点 id
                - node.type 只能使用 start、end、process、decision
                - decision 节点用于最终菱形节点，必须有明确 label/name，并用 edges.label 表达分支出口
                - edge 至少包含 id、source、target、label、type；判断分支 type 使用 branch
                - lane 至少包含 id、label、type；ROLE 使用 type=role，DEPARTMENT 使用 type=department
                - 节点进入泳道时必须明确 laneId，不要靠节点名称猜测
                - sceneType 只能使用 ADMIN、BUSINESS、LEARNING、LIFE；当参数 sceneType=AUTO 时必须返回你最终判断的具体场景，不要返回 AUTO
                - 必须返回 requestedLayoutDirection、resolvedLayoutDirection、requestedDecisionMode、resolvedDecisionMode、requestedSwimlaneMode、resolvedSwimlaneMode
                - requestedLayoutDirection 与 resolvedLayoutDirection 只能是 VERTICAL 或 HORIZONTAL
                - resolvedDecisionMode 只能是 ENABLED 或 DISABLED
                - resolvedSwimlaneMode 只能是 NONE、ROLE 或 DEPARTMENT

                输出格式：
                {
                  "title": "请假审批流程图",
                  "type": "SWIMLANE",
                  "sceneType": "ADMIN",
                  "nodeGranularity": "STANDARD",
                  "requestedLayoutDirection": "VERTICAL",
                  "resolvedLayoutDirection": "VERTICAL",
                  "requestedDecisionMode": "AUTO",
                  "resolvedDecisionMode": "ENABLED",
                  "requestedSwimlaneMode": "AUTO",
                  "resolvedSwimlaneMode": "ROLE",
                  "lanes": [
                    {"id": "employee", "label": "员工", "type": "role"},
                    {"id": "manager", "label": "主管", "type": "role"},
                    {"id": "hr", "label": "HR", "type": "role"}
                  ],
                  "nodes": [
                    {"id": "start", "type": "start", "label": "开始", "name": "开始", "laneId": "employee"},
                    {"id": "submit", "type": "process", "label": "提交申请", "name": "提交申请", "laneId": "employee"},
                    {"id": "approve", "type": "decision", "label": "审核通过？", "name": "审核通过？", "laneId": "manager"},
                    {"id": "archive", "type": "process", "label": "人事备案", "name": "人事备案", "laneId": "hr"}
                  ],
                  "edges": [
                    {"id": "e1", "source": "start", "target": "submit", "label": "", "type": "normal"},
                    {"id": "e2", "source": "submit", "target": "approve", "label": "", "type": "normal"},
                    {"id": "e3", "source": "approve", "target": "archive", "label": "通过", "type": "branch"}
                  ]
                }

                参数原值：
                sceneType=%s
                nodeGranularity=%s
                layoutDirection=%s
                decisionMode=%s
                swimlaneMode=%s
                展示内容：%s

                用户输入：
                %s
                """.formatted(
                sceneInstruction(sceneType),
                granularityInstruction(granularity),
                layoutDirectionInstruction(layoutDirection),
                decisionInstruction(decisionMode),
                swimlaneInstruction(swimlaneMode),
                sceneType,
                granularity,
                layoutDirection,
                decisionMode,
                swimlaneMode,
                request.getDisplayItems() == null || request.getDisplayItems().isEmpty()
                        ? "STEP" : String.join(", ", request.getDisplayItems()),
                normalizedInput
        );
    }

    private FlowchartDTO.FlowchartData parseAndValidate(String content, FlowchartDTO.GenerateRequest request) {
        try {
            FlowchartDTO.FlowchartData data = objectMapper.readValue(extractJson(content), FlowchartDTO.FlowchartData.class);
            validate(data, request);
            return data;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "AI 返回内容不是合法流程图 JSON");
        }
    }

    private void validate(FlowchartDTO.FlowchartData data, FlowchartDTO.GenerateRequest request) {
        if (data == null || !StringUtils.hasText(data.getTitle())) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 title");
        }
        if (data.getNodes() == null || data.getNodes().isEmpty()) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 nodes");
        }
        if (data.getEdges() == null) {
            throw new BusinessException(500, "AI 返回 JSON 缺少 edges");
        }

        String requestedDecisionMode = normalizeDecisionMode(firstText(
                data.getRequestedDecisionMode(), request.getDecisionMode()));
        String requestedSwimlaneMode = normalizeSwimlaneMode(firstText(
                data.getRequestedSwimlaneMode(), request.getSwimlaneMode(), request.getSwimlane()));
        String requestedLayoutDirection = normalizeLayoutDirection(firstText(
                data.getRequestedLayoutDirection(), request.getLayoutDirection()));
        data.setSceneType(normalizeResolvedSceneType(firstText(data.getSceneType(), request.getSceneType(), request.getProcessType())));
        data.setNodeGranularity(normalizeNodeGranularity(firstText(
                data.getNodeGranularity(), request.getNodeGranularity(), request.getNodeLevel())));
        data.setRequestedLayoutDirection(requestedLayoutDirection);
        data.setResolvedLayoutDirection(normalizeLayoutDirection(firstText(
                data.getResolvedLayoutDirection(), requestedLayoutDirection)));
        data.setRequestedDecisionMode(requestedDecisionMode);
        data.setRequestedSwimlaneMode(requestedSwimlaneMode);

        Set<String> nodeIds = new HashSet<>();
        for (FlowchartDTO.Node node : data.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getId())
                    || (!StringUtils.hasText(node.getName()) && !StringUtils.hasText(node.getLabel()))) {
                throw new BusinessException(500, "AI 返回 JSON 存在无效节点");
            }
            if (!nodeIds.add(node.getId())) {
                throw new BusinessException(500, "AI 返回 JSON 存在重复节点 ID");
            }
            if (!StringUtils.hasText(node.getName())) {
                node.setName(node.getLabel());
            }
            if (!StringUtils.hasText(node.getLabel())) {
                node.setLabel(node.getName());
            }
            if (!StringUtils.hasText(node.getType())) {
                node.setType("process");
            }
            node.setType(normalizeNodeType(node.getType()));
            if ("NONE".equals(requestedDecisionMode) && "decision".equals(node.getType())) {
                node.setType("process");
            }
        }
        for (int index = 0; index < data.getEdges().size(); index += 1) {
            FlowchartDTO.Edge edge = data.getEdges().get(index);
            if (edge == null || !nodeIds.contains(edge.getSource()) || !nodeIds.contains(edge.getTarget())) {
                throw new BusinessException(500, "AI 返回 JSON 存在无效连接");
            }
            if (!StringUtils.hasText(edge.getId())) {
                edge.setId("e" + (index + 1));
            }
            if (!StringUtils.hasText(edge.getType())) {
                edge.setType(StringUtils.hasText(edge.getLabel()) || StringUtils.hasText(edge.getCondition())
                        ? "branch" : "normal");
            }
        }
        if (data.getLanes() == null) {
            data.setLanes(List.of());
        }
        data.getLanes().forEach(lane -> {
            if (!StringUtils.hasText(lane.getLabel())) {
                lane.setLabel(defaultText(lane.getName(), lane.getId()));
            }
            if (!StringUtils.hasText(lane.getName())) {
                lane.setName(lane.getLabel());
            }
            if (!StringUtils.hasText(lane.getId())) {
                lane.setId(slug(lane.getLabel()));
            }
            if (!StringUtils.hasText(lane.getType())) {
                lane.setType("DEPARTMENT".equals(requestedSwimlaneMode) ? "department" : "role");
            }
        });
        if ("NONE".equals(requestedSwimlaneMode)) {
            data.setLanes(List.of());
            data.getNodes().forEach(node -> {
                node.setLane(null);
                node.setLaneId(null);
            });
            data.setResolvedSwimlaneMode("NONE");
        } else {
            data.setResolvedSwimlaneMode(normalizeResolvedSwimlaneMode(firstText(data.getResolvedSwimlaneMode(),
                    data.getLanes().isEmpty() ? "NONE" : requestedSwimlaneMode)));
        }
        boolean hasDecision = data.getNodes().stream().anyMatch(node -> "decision".equals(node.getType()));
        data.setResolvedDecisionMode(hasDecision ? "ENABLED" : "DISABLED");
        if (!StringUtils.hasText(data.getType())) {
            data.setType("NONE".equals(data.getResolvedSwimlaneMode()) ? "FLOWCHART" : "SWIMLANE");
        }
    }

    private String sceneInstruction(String sceneType) {
        return switch (sceneType) {
            case "AUTO" -> "【AI 决策｜AUTO】请先根据用户内容判断流程语境，并在 sceneType 返回 ADMIN、BUSINESS、LEARNING、LIFE 之一。不要固定按行政流程理解。";
            case "BUSINESS" -> "【语境偏好｜MEDIUM】请优先按照业务流程语境理解该需求，关注客户、订单、状态流转和业务协作，但不得添加用户未提供且无法合理推断的业务环节。";
            case "LEARNING" -> "【语境偏好｜MEDIUM】请优先按照学习流程语境理解该需求，关注学习步骤、练习、检查和复盘，但不得虚构课程或考核要求。";
            case "LIFE" -> "【语境偏好｜MEDIUM】请优先按照生活流程语境理解该需求，关注日常行动顺序和条件变化，但不得强行行政化。";
            default -> "【语境偏好｜MEDIUM】请优先按照行政流程语境理解该需求，但不得添加用户未提供且无法合理推断的行政审批关系。";
        };
    }

    private String granularityInstruction(String granularity) {
        return switch (granularity) {
            case "SIMPLE" -> "【较强偏好｜MEDIUM-HIGH】节点粒度要求偏简略。请主动合并次要操作，保留核心流程，但不得删除关键分支、审批状态或参与方变化。";
            case "STANDARD" -> "【明确偏好｜MEDIUM】节点粒度要求标准。请平衡完整性与可读性，保留主要步骤和必要说明。";
            case "DETAILED" -> "【较强偏好｜MEDIUM-HIGH】节点粒度要求详细。请主动拆分关键操作、中间步骤、判断和流转过程，但禁止为了凑节点制造不存在的操作。";
            default -> "【AI 决策｜AUTO/SOFT】节点粒度由你根据流程复杂度判断，返回 nodeGranularity 的最终值。";
        };
    }

    private String layoutDirectionInstruction(String layoutDirection) {
        return switch (layoutDirection) {
            case "HORIZONTAL" -> "【展示约束｜HARD】最终流程图必须采用横向显示元数据：requestedLayoutDirection=HORIZONTAL，resolvedLayoutDirection=HORIZONTAL。节点和边仍按真实流程生成，前端将从左到右绘制。";
            default -> "【展示约束｜HARD】最终流程图必须采用纵向显示元数据：requestedLayoutDirection=VERTICAL，resolvedLayoutDirection=VERTICAL。节点和边仍按真实流程生成，前端将自上而下绘制。";
        };
    }

    private String decisionInstruction(String decisionMode) {
        return switch (decisionMode) {
            case "FORCE" -> "【强规则｜HIGH】强制优先使用判断节点。请主动识别真实存在或可直接推导的条件分支，并转换为 decision 节点；禁止虚构不存在的业务判断。";
            case "NONE" -> "【强负约束｜HIGH】禁止使用 decision/diamond 节点，但必须完整保留原始条件逻辑，通过普通节点、带标签连接线或流程描述表达条件结果。";
            default -> "【AI 决策｜AUTO】分析是否存在真实条件、审批结果、成功失败或是否满足条件；存在则使用 decision 节点，否则不要强行添加。";
        };
    }

    private String swimlaneInstruction(String swimlaneMode) {
        return switch (swimlaneMode) {
            case "NONE" -> "【强约束｜HARD】禁止创建任何 swimlane/lane 容器，但保留节点中的角色和部门语义。";
            case "ROLE" -> "【强约束｜HARD】最终流程图必须采用角色泳道。优先从输入内容识别真实参与角色，并将每个流程节点明确分配到对应 role lane。不得改为部门泳道或普通流程。";
            case "DEPARTMENT" -> "【强约束｜HARD】最终流程图必须采用部门泳道。按业务部门、人事部、财务部等部门划分，并使用 department lane；不得退化成员工、主管、HR 等角色泳道。";
            default -> "【AI 决策｜AUTO】请先判断流程是否需要泳道。若存在多个明确参与者或部门且泳道能提升可读性，返回 ROLE 或 DEPARTMENT；否则返回 NONE。";
        };
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
     * 流程图只使用 diagram_flowchart_agent 绑定的文本模型，不做 Leader 或通用 text 兜底。
     */
    private String requireTextConfigPrefix() {
        String bound = resolveAgentBoundModel(FLOWCHART_AGENT_NAME);
        if (!StringUtils.hasText(bound) || !isTextConfigPrefix(bound)) {
            throw new BusinessException(
                    400,
                    "流程图智能体未绑定文本模型，请在系统配置中维护 ai.agent-bindings."
                            + FLOWCHART_AGENT_NAME + ".model"
            );
        }
        if (!hasCompleteConfig(bound)) {
            throw new BusinessException(
                    400,
                    "流程图智能体绑定的文本模型配置不完整，请检查 "
                            + bound + ".provider/base-url/api-key/model"
            );
        }
        return bound;
    }

    private boolean isTextConfigPrefix(String configPrefix) {
        return "ai.service.text".equals(configPrefix)
                || configPrefix.startsWith("ai.service.text.");
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

    private String normalizeSceneType(String value) {
        String text = defaultText(value, "ADMIN").toUpperCase();
        if (text.contains("AUTO") || text.contains("自动")) return "AUTO";
        if (text.contains("BUSINESS") || text.contains("业务")) return "BUSINESS";
        if (text.contains("LEARNING") || text.contains("STUDY") || text.contains("学习")) return "LEARNING";
        if (text.contains("LIFE") || text.contains("生活")) return "LIFE";
        return "ADMIN";
    }

    private String normalizeResolvedSceneType(String value) {
        String text = normalizeSceneType(value);
        return "AUTO".equals(text) ? "ADMIN" : text;
    }

    private String normalizeNodeGranularity(String value) {
        String text = defaultText(value, "AUTO").toUpperCase();
        if (text.contains("SIMPLE") || text.contains("简")) return "SIMPLE";
        if (text.contains("DETAIL") || text.contains("详细")) return "DETAILED";
        if (text.contains("STANDARD") || text.contains("标准")) return "STANDARD";
        return "AUTO";
    }

    private String normalizeLayoutDirection(String value) {
        String text = defaultText(value, "VERTICAL").toUpperCase();
        if (text.contains("HORIZONTAL") || text.contains("LANDSCAPE") || text.contains("横")) return "HORIZONTAL";
        return "VERTICAL";
    }

    private String normalizeDecisionMode(String value) {
        String text = defaultText(value, "AUTO").toUpperCase();
        if (text.contains("FORCE") || text.contains("INCLUDE") || text.contains("强制")) return "FORCE";
        if (text.contains("NONE") || text.contains("LINEAR") || text.contains("不使用") || text.contains("不包含")) return "NONE";
        return "AUTO";
    }

    private String normalizeSwimlaneMode(String value) {
        String text = defaultText(value, "AUTO").toUpperCase();
        if (text.contains("DEPARTMENT") || text.contains("部门")) return "DEPARTMENT";
        if (text.contains("ROLE") || text.contains("角色")) return "ROLE";
        if (text.contains("NONE") || text.contains("HIDDEN") || text.contains("不显示")) return "NONE";
        return "AUTO";
    }

    private String normalizeResolvedSwimlaneMode(String value) {
        String text = normalizeSwimlaneMode(value);
        return "AUTO".equals(text) ? "NONE" : text;
    }

    private String normalizeNodeType(String value) {
        String text = defaultText(value, "process").toLowerCase();
        if (text.contains("start")) return "start";
        if (text.contains("end")) return "end";
        if (text.contains("decision") || text.contains("judge")) return "decision";
        return "process";
    }

    private String slug(String value) {
        String text = defaultText(value, "lane").trim().toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-+|-+$", "");
        return StringUtils.hasText(text) ? text : "lane";
    }
}
