package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

public class KnowledgeChatDTO {

    @Data
    @Schema(description = "Java 知识库智能体聊天请求")
    public static class ChatRequest {
        @NotNull(message = "MaxKB 账号不能为空")
        @Schema(description = "MaxKB 账号 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Long accountId;

        @NotBlank(message = "知识库 ID 不能为空")
        @Size(max = 128, message = "知识库 ID 最多 128 字符")
        @Schema(description = "MaxKB 知识库 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private String knowledgeId;

        @NotBlank(message = "问题不能为空")
        @Size(max = 1000, message = "问题最多 1000 字符")
        @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "这份文档讲了什么？")
        private String question;

        @Size(max = 64, message = "会话 ID 最多 64 字符")
        @Schema(description = "会话 ID，可为空")
        private String sessionId;

        @Size(max = 64, message = "智能体名称最多 64 字符")
        @Schema(description = "你的系统智能体名称；为空时使用 leader_agent", example = "leader_agent")
        private String agentName;

        @Size(max = 128, message = "模型配置最多 128 字符")
        @Schema(description = "本次调用临时指定模型配置；为空时使用智能体绑定模型")
        private String llmModel;

        @Min(value = 1, message = "召回数量至少为 1")
        @Max(value = 20, message = "召回数量最多为 20")
        @Schema(description = "MaxKB 召回数量", example = "5")
        private Integer topNumber;

        @DecimalMin(value = "0", message = "相似度不能小于 0")
        @DecimalMax(value = "2", message = "相似度不能大于 2")
        @Schema(description = "MaxKB 相似度阈值，混合检索最高为 2", example = "0.6")
        private Double similarity;

        @Size(max = 32, message = "检索模式最多 32 字符")
        @Schema(description = "MaxKB 检索模式：embedding / keywords / blend", example = "blend")
        private String searchMode;
    }

    @Data
    @Schema(description = "Java 知识库智能体聊天响应")
    public static class ChatResponse {
        private String sessionId;
        private String sessionToken;
        private String agentName;
        private String model;
        private String answer;
        private String answerType;
        private List<Reference> references;
        private Map<String, Object> metadata;
        private LlmChatResponse llmResponse;
        private Object retrievalRaw;
    }

    @Data
    @Schema(description = "MaxKB 召回引用片段")
    public static class Reference {
        private String id;
        private String title;
        private String documentName;
        private String knowledgeName;
        private String knowledgeType;
        private String content;
        private Double similarity;
        private String source;
        private Map<String, Object> raw;
    }
}
