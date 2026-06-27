package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UserProfileDTO {

    @Data
    public static class DimensionRule {
        private String key;
        private String name;
        private String shortName;
        private String description;
        private List<String> sourceTypes;
        private List<String> evidenceExamples;
        private String updateStrategy;
        private String updatePolicy;
        private Double minConfidence;
        private Integer singleUpdateLimit;
        private String leaderUsage;
        private List<String> validationRules;
        private Integer defaultScore;
        private Double defaultConfidence;
    }

    @Data
    public static class DimensionSnapshot {
        private String key;
        private String name;
        private String shortName;
        private String description;
        private Integer score;
        private Double confidence;
        private String trend;
        private Integer evidenceCount;
        private List<String> sourceSummary;
        private String updatePolicy;
        private LocalDateTime lastUpdatedAt;
    }

    @Data
    public static class RadarSnapshot {
        private Long userId;
        private Integer overallScore;
        private String confidenceLevel;
        private List<String> profileTags;
        private List<String> strongDimensions;
        private List<String> weakDimensions;
        private List<String> resourcePreference;
        private List<DimensionSnapshot> dimensions;
        private List<String> leaderUsageRules;
        private String updateMode;
        private LocalDateTime lastUpdatedAt;
    }

    @Data
    public static class EvidenceRequest {
        @NotBlank(message = "画像维度不能为空")
        @Size(max = 80, message = "画像维度最多 80 字符")
        private String dimensionKey;

        @NotBlank(message = "证据来源不能为空")
        @Size(max = 60, message = "证据来源最多 60 字符")
        private String sourceType;

        @Size(max = 120, message = "来源 ID 最多 120 字符")
        private String sourceId;

        @NotBlank(message = "证据内容不能为空")
        @Size(max = 1000, message = "证据内容最多 1000 字符")
        private String evidence;

        @Size(max = 40, message = "变化方向最多 40 字符")
        private String direction;

        private Double confidence;

        private Integer suggestedDelta;

        private Map<String, Object> metadata;
    }

    @Data
    public static class EvidenceResponse {
        private String dimensionKey;
        private String status;
        private Boolean accepted;
        private Integer appliedDelta;
        private String reason;
        private DimensionSnapshot snapshot;
    }

    @Data
    public static class AdminRulesResponse {
        private List<DimensionRule> rules;
        private List<String> globalRules;
        private List<String> leaderRules;
        private List<String> evidenceFlow;
        private List<EvidenceScoringCriterion> evidenceScoringCriteria;
        private List<SourceReliabilityRule> sourceReliabilityRules;
        private List<ScoreDeltaRule> scoreDeltaRules;
        private List<UpdateDecisionStep> updateDecisionSteps;
        private List<LeaderUsagePolicy> leaderUsagePolicies;
        private List<ConflictPolicy> conflictPolicies;
        private List<String> auditFields;
        private List<String> acceptanceCriteria;
    }

    @Data
    public static class EvidenceScoringCriterion {
        private String key;
        private String name;
        private Integer weight;
        private String description;
        private List<String> highScoreSignals;
        private List<String> lowScoreSignals;
    }

    @Data
    public static class SourceReliabilityRule {
        private String sourceType;
        private Double weight;
        private String reliability;
        private String updatePermission;
        private String example;
    }

    @Data
    public static class ScoreDeltaRule {
        private String level;
        private String evidenceStrength;
        private Integer suggestedDelta;
        private String applyRule;
        private String reviewRule;
    }

    @Data
    public static class UpdateDecisionStep {
        private Integer step;
        private String name;
        private String passCondition;
        private String failAction;
    }

    @Data
    public static class LeaderUsagePolicy {
        private String profileSignal;
        private String allowedUse;
        private String forbiddenUse;
        private String responseStyle;
    }

    @Data
    public static class ConflictPolicy {
        private String scenario;
        private String decisionRule;
        private String evidenceAction;
        private String leaderBehavior;
    }
}
