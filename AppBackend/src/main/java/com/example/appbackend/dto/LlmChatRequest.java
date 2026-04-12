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

    @NotBlank(message = "输入内容不能为空")
    @Size(max = 4000, message = "输入内容最多 4000 字符")
    @Schema(description = "用户输入内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "帮我推荐一个食堂")
    private String input;
}
