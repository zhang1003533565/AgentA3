package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LLM 对话响应")
public class LlmChatResponse {

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "会话唯一标识(session_token)")
    private String sessionToken;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "模型回答")
    private String answer;
}
