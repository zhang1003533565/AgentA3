package com.example.appbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LearningPathDTO {
    public static final Set<String> PYTHON_PROFILE_QUESTION_IDS = Set.of(
            "python_goal",
            "python_level",
            "python_weak_topic",
            "python_resource_preference",
            "python_weekly_time"
    );

    private LearningPathDTO() {
    }

    @Data
    public static class HomeView {
        private Long userId;
        private String courseKey;
        private PathView activePath;
        private List<MasteryView> mastery;
        private UserProfileDTO.RadarSnapshot profile;
        private Integer profileCompleteness;
        private List<String> answeredQuestionIds;
        private List<PathItemView> todayTasks;
        private List<Recommendation> recommendations;
    }

    @Data
    public static class GenerateRequest {
        @NotBlank
        private String courseKey;
        @NotBlank
        @Size(max = 500)
        private String topic;
        private String intent;
        @Size(max = 6)
        private List<String> requestedResourceTypes;
    }

    @Data
    public static class WorkflowError {
        private String message;
        private Boolean retryable;
    }

    @Data
    public static class WorkflowView {
        private String workflowId;
        private String courseKey;
        private String topic;
        private String intent;
        private String status;
        private String stage;
        private Integer progress;
        private String message;
        private String activeAgentName;
        private String activeResourceType;
        private Map<String, AssistantResourceDTO> resources;
        private Map<String, WorkflowError> errors;
        private PathView path;
        private Long messageId;
        private LocalDateTime startedAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class ProfileAnswerRequest {
        @NotBlank
        private String questionId;
        @NotBlank
        @Size(max = 500)
        private String answer;
    }

    @Data
    public static class ProfileAnswerResult {
        private UserProfileDTO.RadarSnapshot profile;
        private List<String> answeredQuestionIds;
        private Integer profileCompleteness;
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
