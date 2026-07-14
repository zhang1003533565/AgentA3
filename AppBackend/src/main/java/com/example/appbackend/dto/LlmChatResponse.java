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

    @Schema(description = "已持久化的助手消息 ID")
    private Long messageId;

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "会话唯一标识(session_token)")
    private String sessionToken;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "兼容旧客户端字段；本地检索策略已移除，通常返回 direct_agent 或工具名")
    private String ragStrategy;

    @Schema(description = "实际执行回答的智能体")
    private String agentName;

    @Schema(description = "AI 整理出的搜索关键词")
    private String searchKeyword;

    @Schema(description = "Java 后端业务接口或第三方知识库返回的候选结果")
    private List<Map<String, Object>> matchedResults;

    @Schema(description = "上下文来源元信息")
    private Map<String, Object> retrievalMeta;

    @Schema(description = "执行轨迹")
    private List<Map<String, Object>> trace;

    @Schema(description = "模型回答")
    private String answer;

    @Schema(description = "回答内容类型：text/markdown/mermaid_mindmap/image_prompt/ppt_outline/ppt_layout/ppt_review/ppt_image_prompt/question_bank/tool_result 等")
    private String answerType;

    @Schema(description = "前端展示主类型：text/image/document/video/diagram 等")
    private String outputType;

    @Schema(description = "前端展示类型集合")
    private List<String> outputTypes;

    @Schema(description = "输出策略、附件数量等展示元信息")
    private Map<String, Object> outputMeta;

    @Schema(description = "结构化附件列表，包含图片、视频、PDF、Word、PPT 等")
    private List<Map<String, Object>> attachments;

    @Schema(description = "统一助手资源列表")
    private List<AssistantResourceDTO> resources;

    @Schema(description = "回答来源与生成证据链")
    private AssistantEvidenceChainDTO evidenceChain;
}
