package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "LLM 对话请求")
public class LlmChatRequest {

    @Size(max = 64, message = "会话 ID 最多 64 字符")
    @Schema(description = "会话 ID，可为空；为空时服务端自动创建", example = "session-001")
    private String sessionId;

    @Size(max = 2000, message = "提示词最多 2000 字符")
    @Schema(description = "系统提示词，可为空", example = "你是智慧校园助手，请简洁回答。")
    private String prompt;

    @Size(max = 64, message = "兼容策略字段最多 64 字符")
    @Schema(description = "兼容旧客户端字段；AI Server 已移除本地检索策略，当前会忽略该值", example = "direct_agent")
    private String ragStrategy;

    @Size(max = 64, message = "智能体名称最多 64 字符")
    @Schema(description = "指定智能体，可为空；为空或 leader_agent 时由 Leader 自动路由", example = "ppt_outline_agent")
    private String agentName;

    @Size(max = 128, message = "LLM 模型标识最多 128 字符")
    @Schema(description = "可选：本次请求临时指定 LLM 模型配置前缀；为空时使用 ai.agent-bindings.{agentName}.model 绑定", example = "ai.service.text.deepseek-chat")
    private String llmModel;

    @NotBlank(message = "输入内容不能为空")
    @Size(max = 4000, message = "输入内容最多 4000 字符")
    @Schema(description = "用户输入内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "帮我推荐一个食堂")
    private String input;
}
