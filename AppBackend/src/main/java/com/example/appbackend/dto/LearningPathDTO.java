package com.example.appbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class LearningPathDTO {
    private LearningPathDTO() {
    }

    @Data
    public static class HomeView {
        private Long userId;
        private String courseKey;
        private PathView activePath;
        private List<MasteryView> mastery;
    }

    @Data
    public static class PathDraft {
        @NotBlank
        private String courseKey;
        @NotBlank
        private String goal;
        @NotBlank
        private String profileDigest;
        @NotBlank
        private String masteryDigest;
        private Long sourceMessageId;
        private LocalDateTime generatedAt;
        private LocalDateTime nextReplanAt;
        @Valid
        @NotEmpty
        private List<PathItemDraft> items;
    }

    @Data
    public static class PathItemDraft {
        @NotBlank
        private String itemKey;
        @NotBlank
        private String knowledgePoint;
        @NotBlank
        private String objective;
        @NotNull
        private BigDecimal targetMastery;
        @NotNull
        private Integer priority;
        @NotNull
        private Integer sequenceNo;
        private List<String> resourceKinds;
        private List<String> resourceIds;
        private String status;
        private String deliveryStatus;
        private Long sourceMessageId;
        private LocalDateTime scheduledAt;
        private String rationale;
    }

    @Data
    public static class PathView {
        private Long id;
        private Long userId;
        private String courseKey;
        private String goal;
        private Integer version;
        private String status;
        private String profileDigest;
        private String masteryDigest;
        private Long sourceMessageId;
        private LocalDateTime generatedAt;
        private LocalDateTime nextReplanAt;
        private List<PathItemView> items;
    }

    @Data
    public static class PathItemView {
        private Long id;
        private Long pathId;
        private String itemKey;
        private String knowledgePoint;
        private String objective;
        private BigDecimal targetMastery;
        private Integer priority;
        private Integer sequenceNo;
        private List<String> resourceKinds;
        private List<String> resourceIds;
        private String status;
        private String deliveryStatus;
        private Long sourceMessageId;
        private LocalDateTime scheduledAt;
        private LocalDateTime deliveredAt;
        private LocalDateTime completedAt;
        private String rationale;
    }

    @Data
    public static class MasteryView {
        private Long id;
        private Long userId;
        private String courseKey;
        private String knowledgePointKey;
        private String knowledgePointName;
        private Long lastAttemptId;
        private Integer attemptCount;
        private Integer correctCount;
        private Integer wrongCount;
        private BigDecimal score;
        private BigDecimal confidence;
        private String status;
        private LocalDateTime nextReviewAt;
        private Long version;
    }

    @Data
    public static class AssessmentObservation {
        @NotNull
        private Long userId;
        @NotNull
        private Long attemptId;
        @NotBlank
        private String courseKey;
        @NotBlank
        private String knowledgePointKey;
        private String knowledgePointName;
        @NotNull
        private Boolean correct;
        private String difficulty;
    }

    @Data
    public static class InteractionRequest {
        @NotBlank
        private String action;
    }

    @Data
    public static class Recommendation {
        private Long itemId;
        private String itemKey;
        private String knowledgePoint;
        private String objective;
        private Integer priority;
        private List<String> resourceIds;
        private String status;
        private String rationale;
    }
}
