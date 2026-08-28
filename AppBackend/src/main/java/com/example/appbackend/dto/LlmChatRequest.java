package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

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

    @Size(max = 4000, message = "输入内容最多 4000 字符")
    @Schema(description = "用户输入内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "帮我推荐一个食堂")
    private String input;

    @Schema(description = "随本次提问上传的资源，支持图片、文档、表格、演示文稿、音视频和压缩包")
    private List<Map<String, Object>> attachments;

    @Pattern(regexp = "^(transform|retry)$", message = "交互类型仅支持 transform 或 retry")
    @Schema(description = "可选的结构化会话操作；普通输入不传", example = "transform")
    private String interactionType;

    @Size(max = 160, message = "操作展示文本最多 160 字符")
    @Schema(description = "结构化操作在聊天记录中的简短展示文本", example = "已请求：生成文件版")
    private String displayInput;

    @Size(max = 32, message = "目标输出类型最多 32 字符")
    @Pattern(regexp = "^(|text|document|image|video|audio|diagram|formula|question)$", message = "目标输出类型不受支持")
    @Schema(description = "结构化转换的目标输出类型", example = "document")
    private String requestedOutputType;

    @Positive(message = "来源消息 ID 必须为正数")
    @Schema(description = "结构化操作所针对的助手消息 ID", example = "1024")
    private Long sourceMessageId;
}
