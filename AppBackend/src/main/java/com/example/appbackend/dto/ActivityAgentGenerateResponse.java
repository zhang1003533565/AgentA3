package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "AI 活动草稿生成响应")
public class ActivityAgentGenerateResponse {

    @Schema(description = "动作：clarify-需追问 / draft-草稿待确认 / ready-可直接回填", example = "draft")
    private String action;

    @Schema(description = "给管理员的自然语言回复/追问")
    private String reply;

    @Schema(description = "活动草稿，恰好 10 个字段（title/organizerName/coverImage/categoryId/maxPeople/location/startTime/endTime/signupEndTime/content），null 表示未确认")
    private Map<String, Object> activity;

    @Schema(description = "AI 生成未确认的字段（title/content）")
    private List<String> generatedFields;

    @Schema(description = "仍缺失的必填字段")
    private List<String> missingFields;

    @Schema(description = "已确认字段")
    private List<String> confidentFields;

    @Schema(description = "非阻塞提示")
    private List<String> warnings;

    @Schema(description = "实际执行模型（可选）")
    private String model;

    @Schema(description = "实际执行智能体")
    private String agentName;

    @Schema(description = "AI 原始返回 JSON（排查用）")
    private String rawAnswer;
}
