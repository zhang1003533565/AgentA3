package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "AI 活动草稿生成请求")
public class ActivityAgentGenerateRequest {

    @NotBlank(message = "输入内容不能为空")
    @Size(max = 4000, message = "输入内容最多 4000 字符")
    @Schema(description = "管理员本轮自然语言输入", requiredMode = Schema.RequiredMode.REQUIRED, example = "我要办一个校园歌手大赛，9月10日下午2点到6点，在大学生活动中心，500人，分类文艺活动")
    private String input;

    @Schema(description = "当前活动草稿，恰好 10 个字段，null 表示未确认；首轮可为空")
    private Map<String, Object> activityDraft;

    @Schema(description = "上一轮 AI 生成未确认的字段（仅允许 title/content）")
    private List<String> generatedFields;

    @Size(max = 64, message = "会话 ID 最多 64 字符")
    @Schema(description = "会话标识，仅透传/日志，不落库")
    private String sessionId;

    @Pattern(regexp = "^ai\\.service\\..*", message = "模型参数必须是 ai.service.* 配置前缀")
    @Schema(description = "模型配置前缀，如 ai.service.text；为空时使用后台 agent 绑定")
    private String llmModel;

    @Schema(description = "最近对话上下文（可选，仅用于指代理解）")
    private Map<String, Object> conversationContext;
}
