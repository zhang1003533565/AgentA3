package com.example.appbackend.service.support;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class MindMapGenerationConstraints {
    public static final String CENTER_AUTO = "AUTO";
    public static final String CENTER_USER_DEFINED = "USER_DEFINED";

    private final String requestedCenterTopicMode;
    private final String resolvedCenterTopic;
    private final String requestedDepth;
    private final int resolvedDepth;
    private final String requestedStructure;
    private final String resolvedStructure;
    private final String detailLevel;

    private MindMapGenerationConstraints(String requestedCenterTopicMode,
                                         String resolvedCenterTopic,
                                         String requestedDepth,
                                         int resolvedDepth,
                                         String requestedStructure,
                                         String resolvedStructure,
                                         String detailLevel) {
        this.requestedCenterTopicMode = requestedCenterTopicMode;
        this.resolvedCenterTopic = resolvedCenterTopic;
        this.requestedDepth = requestedDepth;
        this.resolvedDepth = resolvedDepth;
        this.requestedStructure = requestedStructure;
        this.resolvedStructure = resolvedStructure;
        this.detailLevel = detailLevel;
    }

    public static MindMapGenerationConstraints resolve(String centerTopicMode,
                                                       String centerTopic,
                                                       String depth,
                                                       String structure,
                                                       String detail,
                                                       String inputText) {
        String requestedCenterMode = normalizeCenterTopicMode(centerTopicMode, centerTopic);
        String resolvedCenter = CENTER_USER_DEFINED.equals(requestedCenterMode)
                ? defaultText(centerTopic, "")
                : MindMapTopicExtractor.extract(centerTopic, inputText, "", "");
        String requestedDepth = normalizeDepth(depth);
        int resolvedDepth = "AUTO".equals(requestedDepth) ? inferDepth(inputText) : Integer.parseInt(requestedDepth);
        String requestedStructure = normalizeStructure(structure);
        String resolvedStructure = "AUTO".equals(requestedStructure) ? inferStructure(inputText, resolvedCenter) : requestedStructure;
        String detailLevel = normalizeDetail(detail);
        return new MindMapGenerationConstraints(
                requestedCenterMode,
                resolvedCenter,
                requestedDepth,
                resolvedDepth,
                requestedStructure,
                resolvedStructure,
                detailLevel
        );
    }

    public String requestedCenterTopicMode() {
        return requestedCenterTopicMode;
    }

    public String resolvedCenterTopic() {
        return resolvedCenterTopic;
    }

    public String requestedDepth() {
        return requestedDepth;
    }

    public int resolvedDepth() {
        return resolvedDepth;
    }

    public String requestedStructure() {
        return requestedStructure;
    }

    public String resolvedStructure() {
        return resolvedStructure;
    }

    public String detailLevel() {
        return detailLevel;
    }

    public boolean isUserDefinedCenterTopic() {
        return CENTER_USER_DEFINED.equals(requestedCenterTopicMode) && StringUtils.hasText(resolvedCenterTopic);
    }

    public int siblingLimit() {
        return switch (detailLevel) {
            case "SIMPLE" -> 4;
            case "DETAILED" -> 8;
            default -> 6;
        };
    }

    public String promptInstructions() {
        return """
                【最高优先级】
                只根据用户当前补充要求、用户指定中心主题、文件和原始输入中的真实内容生成思维导图。
                用户补充要求优先于文件内容；文件负责提供知识来源，补充文字负责限定范围、重点、忽略内容和组织方式。
                不得虚构原始内容不存在的知识事实。

                %s

                %s

                %s

                %s

                【硬质量规则】
                父子节点必须具有明确的信息增量，禁止同义重复节点、空洞节点和“关于/相关/介绍/基本概念”等无意义层级。
                禁止把用户废话逐句转换成节点，必须先提取事实、识别概念、合并重复信息。
                节点标题优先使用名词或短语，尽量简洁可扫描；详细模式也不能把长段文字塞进标题。
                同一级节点尽量保持相同分类维度，避免大量同级节点失控；必要时先归类再展开。
                禁止为了满足层级或详细程度制造不存在的节点。
                """.formatted(centerTopicInstruction(), depthInstruction(), structureInstruction(), detailInstruction());
    }

    private String centerTopicInstruction() {
        if (isUserDefinedCenterTopic()) {
            return """
                    【硬约束】
                    最终思维导图中心节点必须严格使用用户指定的中心主题：“%s”。
                    不得改写、扩展或替换该中心主题。
                    """.formatted(resolvedCenterTopic);
        }
        return """
                【AUTO 决策】
                中心主题由 AI 从用户内容中提取最具概括性的主题，不要简单取第一句话。
                可参考系统预提取主题：“%s”，但若内容中存在更准确概括，应返回更准确的 resolvedCenterTopic。
                """.formatted(StringUtils.hasText(resolvedCenterTopic) ? resolvedCenterTopic : "无");
    }

    private String depthInstruction() {
        if ("AUTO".equals(requestedDepth)) {
            return """
                    【AUTO 决策】
                    层级深度由 AI 根据输入长度、主题复杂度、知识结构和分支数量自动选择。
                    当前建议最大深度为 %d 层。中心主题为 Level 0，第一层主要分支为 Level 1。
                    """.formatted(resolvedDepth);
        }
        return """
                【硬约束】
                导图最大层级深度为 %d 层。中心主题为 Level 0，第一层主要分支为 Level 1。
                任何分支均不得超过该深度；不要求每个分支必须达到 %d 层。
                禁止为了凑层级制造无意义节点。
                """.formatted(resolvedDepth, resolvedDepth);
    }

    private String structureInstruction() {
        if ("AUTO".equals(requestedStructure)) {
            return """
                    【AUTO 决策】
                    结构方式由 AI 判断为 KNOWLEDGE、COURSE、REVIEW 或 PROJECT。
                    当前建议结构为 %s，但必须以输入内容最适合的组织策略为准。
                    """.formatted(resolvedStructure);
        }
        return switch (resolvedStructure) {
            case "COURSE" -> """
                    【较强组织偏好】
                    请将内容优先组织成课程知识模块，使其具有清晰的学习结构和模块归属。
                    不得虚构不存在的课程阶段、章节或教学内容。
                    """;
            case "REVIEW" -> """
                    【较强组织偏好】
                    请按照复习提纲方式压缩和组织内容，突出核心知识、关键概念、重要关系和易混内容。
                    禁止在缺乏依据时虚构“必考”“高频考点”等信息。
                    """;
            case "PROJECT" -> """
                    【较强组织偏好】
                    请按照项目拆解方式组织已有内容，优先呈现目标、功能模块、任务、依赖、技术实现和交付物。
                    不得为非项目类内容虚构开发阶段、上线部署或测试任务。
                    """;
            default -> """
                    【较强组织偏好】
                    请以知识梳理方式组织内容，优先提取概念、分类、原理、特点、组成、关系和应用等知识关系。
                    不得为了满足模板而创造原始内容中不存在的知识。
                    """;
        };
    }

    private String detailInstruction() {
        return switch (detailLevel) {
            case "SIMPLE" -> """
                    【较强精简偏好】
                    展开程度为简洁。主动提炼、合并相近知识、删除重复表达和次要节点。
                    简洁只影响横向丰富度和节点信息量，绝不能降低最大层级深度。
                    """;
            case "DETAILED" -> """
                    【较强展开偏好】
                    展开程度为详细。增加有价值的同级分支，拆分重要子概念，保留必要关系信息。
                    详细只影响横向丰富度和节点信息量，绝不能突破最大层级深度，也不能生成同义节点。
                    """;
            default -> """
                    【普通偏好】
                    展开程度为标准。在信息完整和阅读清晰之间平衡，保留主要概念、必要子项和必要说明。
                    展开程度不改变最大层级深度。
                    """;
        };
    }

    private static String normalizeCenterTopicMode(String value, String centerTopic) {
        String normalized = defaultText(value, "").toUpperCase(Locale.ROOT);
        if (CENTER_USER_DEFINED.equals(normalized)) {
            return CENTER_USER_DEFINED;
        }
        if (CENTER_AUTO.equals(normalized)) {
            return CENTER_AUTO;
        }
        return StringUtils.hasText(centerTopic) ? CENTER_USER_DEFINED : CENTER_AUTO;
    }

    private static String normalizeDepth(String value) {
        String normalized = defaultText(value, "AUTO").toUpperCase(Locale.ROOT);
        if (normalized.contains("2")) return "2";
        if (normalized.contains("3")) return "3";
        if (normalized.contains("4")) return "4";
        return "AUTO";
    }

    private static String normalizeStructure(String value) {
        String normalized = defaultText(value, "AUTO").toLowerCase(Locale.ROOT);
        if (normalized.contains("课程") || normalized.contains("course")) return "COURSE";
        if (normalized.contains("复习") || normalized.contains("review") || normalized.contains("exam")) return "REVIEW";
        if (normalized.contains("项目") || normalized.contains("project") || normalized.contains("task")) return "PROJECT";
        if (normalized.contains("知识") || normalized.contains("knowledge")) return "KNOWLEDGE";
        return "AUTO";
    }

    private static String normalizeDetail(String value) {
        String normalized = defaultText(value, "STANDARD").toLowerCase(Locale.ROOT);
        if (normalized.contains("simple") || normalized.contains("简洁")) return "SIMPLE";
        if (normalized.contains("detail") || normalized.contains("详细") || normalized.contains("完整")) return "DETAILED";
        return "STANDARD";
    }

    private static int inferDepth(String inputText) {
        int length = defaultText(inputText, "").length();
        if (length < 180) return 2;
        if (length > 3000) return 4;
        return 3;
    }

    private static String inferStructure(String inputText, String centerTopic) {
        String text = (defaultText(inputText, "") + " " + defaultText(centerTopic, "")).toLowerCase(Locale.ROOT);
        if (text.contains("项目") || text.contains("任务") || text.contains("需求") || text.contains("project")) {
            return "PROJECT";
        }
        if (text.contains("复习") || text.contains("提纲") || text.contains("考试") || text.contains("review")) {
            return "REVIEW";
        }
        if (text.contains("课程") || text.contains("教学") || text.contains("模块") || text.contains("学习路线") || text.contains("course")) {
            return "COURSE";
        }
        return "KNOWLEDGE";
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
