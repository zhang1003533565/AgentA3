package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExamQuestionDTO {

    @Data
    @Schema(description = "题库导入/校验请求，兼容 AI Server 题库 JSON 顶层结构")
    public static class ImportRequest {
        @Schema(description = "题目数组")
        private List<Map<String, Object>> questions = new ArrayList<>();

        @Schema(description = "缺失信息；AI Server 信息不足时会返回")
        private List<String> missingInfo = new ArrayList<>();

        @Size(max = 80, message = "来源智能体最多 80 字符")
        @Schema(description = "来源智能体", example = "textbook_question_single_choice_agent")
        private String sourceAgent;

        @Size(max = 160, message = "来源标题最多 160 字符")
        @Schema(description = "来源标题或批次名称")
        private String sourceTitle;

        @Size(max = 40, message = "来源场景最多 40 字符")
        @Schema(description = "来源场景：test/import/manual", example = "test")
        private String sourceScene;
    }

    @Data
    @Schema(description = "题库 JSON 审查结果")
    public static class ReviewResponse {
        private Boolean valid;
        private List<String> issues = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private Integer questionCount;
        private List<String> types = new ArrayList<>();
    }

    @Data
    @Schema(description = "题库导入结果")
    public static class ImportResponse extends ReviewResponse {
        private Integer importedCount;
        private List<Long> questionIds = new ArrayList<>();
    }

    @Data
    @Schema(description = "单题新增/编辑请求（题库管理页面手工录题）")
    public static class SaveRequest {
        @NotBlank(message = "题型不能为空")
        @Schema(description = "题型", example = "single_choice")
        private String type;

        @NotBlank(message = "题目内容不能为空")
        @Schema(description = "题目内容（题干）")
        private String content;

        @Size(max = 160, message = "所属题库最多 160 字符")
        @Schema(description = "所属题库（对应 sourceTitle）")
        private String bankId;

        @NotBlank(message = "难度不能为空")
        @Schema(description = "难度：easy/medium/hard", example = "easy")
        private String difficulty;

        @NotBlank(message = "答案不能为空")
        @Schema(description = "答案文本")
        private String answer;

        @Schema(description = "解析（可选）")
        private String analysis;
    }

    @Data
    @Schema(description = "题库题目详情")
    public static class QuestionVO {
        private Long id;
        private String sourceQuestionId;
        private String type;
        private String stem;
        private BigDecimal score;
        private String difficulty;
        private Object knowledgePoints;
        private Object tags;
        private Object body;
        private Object answer;
        private String analysis;
        private Object scoring;
        private Object sourceBasis;
        private Object rawQuestion;
        private String sourceAgent;
        private String sourceTitle;
        private String sourceScene;
        private Long createdBy;
        private String visibility;
        private Long ownerUserId;
        private Boolean ownedByCurrentUser;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
