package com.example.appbackend.dto;

import com.example.appbackend.entity.ExamPaperAttempt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AppExamDTO {
    private AppExamDTO() {}

    @Data
    public static class PaperSummary {
        private Long id;
        private String title;
        private String subtitle;
        private Integer durationMinutes;
        private Integer questionCount;
        private BigDecimal totalScore;
        private LocalDateTime publishTime;
        private long attemptCount;
        private Long inProgressAttemptId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaperDetail extends PaperSummary {
        private String precautions;
    }

    /** Deliberately excludes answerJson, analysis and scoringJson. */
    @Data
    public static class QuestionForAttempt {
        private Long id;
        private Long questionId;
        private Integer sortOrder;
        private Integer sectionOrder;
        private BigDecimal score;
        private String type;
        private String stem;
        private String bodyJson;
        private String userAnswerJson;
        private Long version;
        private Boolean answered;
    }

    @Data
    public static class AttemptDetail {
        private Long id;
        private Long paperId;
        private Integer attemptNo;
        private ExamPaperAttempt.Status status;
        private LocalDateTime startedAt;
        private LocalDateTime deadlineAt;
        private LocalDateTime serverNow;
        private LocalDateTime submittedAt;
        private Integer answeredCount;
        private Integer questionCount;
        private List<QuestionForAttempt> questions;
    }

    @Data
    public static class SaveAnswerRequest {
        @NotBlank
        private String answerJson;
        @NotNull
        private Long version;
    }

    @Data
    public static class SavedAnswer {
        private Long paperQuestionId;
        private String answerJson;
        private Long version;
        private Boolean answered;
    }

    @Data
    public static class QuestionResult {
        private Long id;
        private Long questionId;
        private Integer sortOrder;
        private BigDecimal maxScore;
        private String type;
        private String stem;
        private String bodyJson;
        private String userAnswerJson;
        private String answerJson;
        private String analysis;
        private String scoringJson;
        private Boolean answered;
        private Boolean correct;
        private BigDecimal score;
    }

    @Data
    public static class AttemptResult {
        private Long id;
        private Long paperId;
        private Integer attemptNo;
        private ExamPaperAttempt.Status status;
        private LocalDateTime startedAt;
        private LocalDateTime submittedAt;
        private BigDecimal objectiveScore;
        private BigDecimal objectiveTotalScore;
        private List<QuestionResult> questions;
    }

    @Data
    public static class AttemptHistoryItem {
        private Long id;
        private Integer attemptNo;
        private ExamPaperAttempt.Status status;
        private LocalDateTime startedAt;
        private LocalDateTime submittedAt;
        private BigDecimal objectiveScore;
        private BigDecimal objectiveTotalScore;
    }
}
