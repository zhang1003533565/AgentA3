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
            new ToolDefinition("generate_mind_map_image_tool", "思维导图生成工具", "思维导图生成工具（generate_mind_map_image_tool）",
                    "diagram_export", "图表导出", "生成思维导图图片"),
            new ToolDefinition("generate_flowchart_image_tool", "流程图生成工具", "流程图生成工具（generate_flowchart_image_tool）",
                    "diagram_export", "图表导出", "生成流程图图片"),
            new ToolDefinition("generate_activity_image_tool", "活动图生成工具", "活动图生成工具（generate_activity_image_tool）",
                    "diagram_export", "图表导出", "生成活动图/泳道图图片"),
            new ToolDefinition("generate_architecture_image_tool", "架构图生成工具", "架构图生成工具（generate_architecture_image_tool）",
                    "diagram_export", "图表导出", "生成系统架构图图片"),
            new ToolDefinition("generate_knowledge_graph_image_tool", "知识图谱生成工具", "知识图谱生成工具（generate_knowledge_graph_image_tool）",
                    "diagram_export", "图表导出", "生成知识图谱图片"),
            new ToolDefinition("generate_ppt_image_tool", "PPT配图生成工具", "PPT配图生成工具（generate_ppt_image_tool）",
                    "presentation_generation", "PPT 生成", "生成 PPT 封面或页面配图"),
            new ToolDefinition("text_to_sql", "结构化查询工具", "结构化查询工具（text_to_sql）",
                    "structured_query", "结构化查询", "将自然语言转为 SQL 查询业务数据"),
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
            new ToolDefinition("generated_export_tools", "内容导出工具", "内容导出工具（generated_export_tools）",
                    "content_export", "内容整理", "将内容导出为 Markdown/Word/Excel/PPT 等格式"),
            new ToolDefinition("text_to_file_tool", "文本转文件工具", "文本转文件工具（text_to_file_tool）",
                    "content_export", "内容处理", "把用户提供的文本按原文导出为 Markdown/纯文本/Word/PPT/PDF 文件"),
            new ToolDefinition("markdown_export_tool", "Markdown 导出工具", "Markdown 导出工具（markdown_export_tool）",
                    "content_export", "内容整理", "导出为 Markdown 文件"),
            new ToolDefinition("docx_export_tool", "Word 导出工具", "Word 导出工具（docx_export_tool）",
                    "content_export", "内容整理", "导出为 Word 文档"),
            new ToolDefinition("excel_export_tool", "Excel 导出工具", "Excel 导出工具（excel_export_tool）",
                    "content_export", "内容整理", "导出为 Excel 表格"),
            new ToolDefinition("pptx_export_tool", "PPT 导出工具", "PPT 导出工具（pptx_export_tool）",
                    "content_export", "内容整理", "导出为 PPT 幻灯片"),
            new ToolDefinition("content_archive_tool", "附件打包工具", "附件打包工具（content_archive_tool）",
                    "content_export", "内容整理", "将多个附件打包为 ZIP"),
            new ToolDefinition("diagram_source_export_tool", "图表源码导出工具", "图表源码导出工具（diagram_source_export_tool）",
                    "diagram_export", "图表导出", "导出 Mermaid 图表源码"),
            new ToolDefinition("tool_capability_query", "能力查询工具", "能力查询工具（tool_capability_query）",
                    "internal_routing", "内部路由", "查询当前已启用的工具能力")
    );
}
