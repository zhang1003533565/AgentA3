package com.example.appbackend.controller;

import com.example.appbackend.entity.AiToolCallRecord;
import com.example.appbackend.entity.Result;
import com.example.appbackend.repository.AiToolCallRecordRepository;
import com.example.appbackend.repository.SystemConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/tool-monitor")
@Tag(name = "AI 工具调用监控", description = "管理员查看工具调用打分记录")
public class AiToolMonitorController {

    private static final String TOOL_ENABLED_PREFIX = "ai.tool-enabled.";

    private final AiToolCallRecordRepository recordRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    public AiToolMonitorController(AiToolCallRecordRepository recordRepository,
                                   SystemConfigRepository systemConfigRepository,
                                   ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tools")
    @Operation(summary = "获取所有工具列表及启用状态")
    public Result<List<Map<String, Object>>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition definition : TOOL_DEFINITIONS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", definition.name);
            item.put("zhName", definition.zhName);
            item.put("displayName", definition.displayName);
            item.put("category", definition.category);
            item.put("categoryLabel", definition.categoryLabel);
            item.put("purpose", definition.purpose);
            item.put("enabled", isToolEnabled(definition.name));
            tools.add(item);
        }
        return Result.success(tools);
    }

    @GetMapping("/records")
    @Operation(summary = "分页获取工具调用打分记录")
    public Result<Map<String, Object>> listRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        Page<AiToolCallRecord> page = recordRepository.findAllByOrderByCreateTimeDescIdDesc(
                PageRequest.of(safePage - 1, safeSize));
        List<Map<String, Object>> records = page.getContent().stream()
                .map(this::toRecordItem)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", page.getTotalElements());
        result.put("pageNum", safePage);
        result.put("pageSize", safeSize);
        return Result.success(result);
    }

    private Map<String, Object> toRecordItem(AiToolCallRecord record) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.getId());
        item.put("toolName", record.getToolName());
        item.put("toolDisplayName", record.getToolDisplayName());
        item.put("userInput", record.getUserInput());
        item.put("intent", record.getIntent());
        item.put("toolCalled", Boolean.TRUE.equals(record.getToolCalled()));
        item.put("createTime", record.getCreateTime());
        List<Map<String, Object>> candidates = readCandidateTools(record.getCandidateToolsJson());
        item.put("candidateTools", candidates);
        return item;
    }

    private List<Map<String, Object>> readCandidateTools(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isToolEnabled(String toolName) {
        String configKey = TOOL_ENABLED_PREFIX + toolName;
        return systemConfigRepository.findByConfigKeyAndStatus(configKey, 1)
                .map(config -> {
                    String value = config.getConfigValue();
                    if (value == null) return true;
                    String normalized = value.trim().toLowerCase();
                    return !"0".equals(normalized) && !"false".equals(normalized)
                            && !"off".equals(normalized) && !"disabled".equals(normalized);
                })
                .orElse(true);
    }

    private record ToolDefinition(String name, String zhName, String displayName,
                                  String category, String categoryLabel, String purpose) {}

    private static final List<ToolDefinition> TOOL_DEFINITIONS = List.of(
            new ToolDefinition("recognize_image_tool", "图片识别工具", "图片识别工具（recognize_image_tool）",
                    "vision_understanding", "图片理解", "识别上传图片内容并读取文字"),
            new ToolDefinition("generate_image_tool", "图片生成工具", "图片生成工具（generate_image_tool）",
                    "vision_understanding", "图片理解", "根据描述生成图片素材"),
            new ToolDefinition("generate_mind_map_tool", "思维导图生成工具", "思维导图生成工具（generate_mind_map_tool）",
                    "structured_diagram", "结构化图表", "生成可编辑思维导图 JSON；模型绑定 diagram_mind_map_agent"),
            new ToolDefinition("generate_flowchart_tool", "流程图生成工具", "流程图生成工具（generate_flowchart_tool）",
                    "structured_diagram", "结构化图表", "生成可编辑流程图 JSON；模型绑定 diagram_flowchart_agent"),
            new ToolDefinition("generate_architecture_tool", "架构图生成工具", "架构图生成工具（generate_architecture_tool）",
                    "structured_diagram", "结构化图表", "生成可编辑架构图 JSON；模型绑定 diagram_architecture_agent"),
            new ToolDefinition("java_schedule_api", "课表查询工具", "课表查询工具（java_schedule_api）",
                    "campus_service", "系统能力", "查询用户课程安排"),
            new ToolDefinition("java_activity_api", "活动查询工具", "活动查询工具（java_activity_api）",
                    "campus_service", "系统能力", "查询校园活动信息"),
            new ToolDefinition("java_meeting_api", "会议查询工具", "会议查询工具（java_meeting_api）",
                    "campus_service", "系统能力", "查询会议列表和状态"),
            new ToolDefinition("java_canteen_api", "食堂餐饮查询工具", "食堂餐饮查询工具（java_canteen_api）",
                    "campus_service", "系统能力", "查询食堂档口和菜品"),
            new ToolDefinition("java_facility_api", "设施位置查询工具", "设施位置查询工具（java_facility_api）",
                    "campus_service", "系统能力", "查询校园设施位置"),
            new ToolDefinition("java_secondhand_api", "旧物查询工具", "旧物查询工具（java_secondhand_api）",
                    "campus_service", "系统能力", "查询二手商品信息"),
            new ToolDefinition("text_to_markdown_tool", "Markdown 内容整理", "Markdown 内容整理（text_to_markdown_tool）",
                    "content_export", "内容整理", "整理/导出 Markdown 文件"),
            new ToolDefinition("text_to_txt_tool", "TXT 内容整理", "TXT 内容整理（text_to_txt_tool）",
                    "content_export", "内容整理", "按原文导出纯文本文件"),
            new ToolDefinition("text_to_docx_tool", "Word 内容整理", "Word 内容整理（text_to_docx_tool）",
                    "content_export", "内容整理", "整理/导出 Word 文档"),
            new ToolDefinition("diagram_source_export_tool", "图表源码导出工具", "图表源码导出工具（diagram_source_export_tool）",
                    "diagram_export", "图表导出", "导出 Mermaid 图表源码"),
            new ToolDefinition("tool_capability_query", "能力查询工具", "能力查询工具（tool_capability_query）",
                    "internal_routing", "内部路由", "查询当前已启用的工具能力")
    );
}
