package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    @Schema(description = "实际使用的 RAG 策略")
    private String ragStrategy;

    @Schema(description = "AI 整理出的搜索关键词")
    private String searchKeyword;

    @Schema(description = "本地匹配到的候选结果")
    private List<Map<String, Object>> matchedResults;

    @Schema(description = "检索元信息")
    private Map<String, Object> retrievalMeta;

    @Schema(description = "执行轨迹")
    private List<Map<String, Object>> trace;

    @Schema(description = "模型回答")
    private String answer;
}
