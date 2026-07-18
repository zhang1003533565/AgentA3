package com.example.appbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QuestionAssemblyDTO {

    private QuestionAssemblyDTO() {
    }

    @Data
    public static class AssemblyRequest {
        @NotBlank
        private String mode;
        private String basisMode;
        private String sourceType;
        private String topic;
        private String text;
        private String sourceTitle;

        @Valid
        @NotEmpty
        private List<AssemblyRule> rules = new ArrayList<>();
    }

    @Data
    public static class AssemblyRule {
        @NotBlank
        private String type;

        @Min(1)
        @Max(100)
        private Integer quantity;

        private String difficulty;
    }

    @Data
    public static class AssemblyOptions {
        private List<String> modes = List.of("existing", "generate", "hybrid");
        private List<String> basisModes = List.of(
                "text", "file", "uploaded_question_bank", "knowledge_agent");
        private List<QuestionGenerationDTO.QuestionTypeOption> questionTypes = new ArrayList<>();
    }

    @Data
    public static class AssemblyQuestion {
        private String origin;
        private Long existingQuestionId;
        private String generatedBy;
        private String type;
        private Map<String, Object> question;
    }

    @Data
    public static class AssemblyResponse {
        private String draftId;
        private String mode;
        private String basisMode;
        private String basisAgent;
        private String sourceTitle;
        private Integer requestedCount;
        private Integer existingCount;
        private Integer generatedCount;
        private Integer missingCount;
        private List<AssemblyQuestion> questions = new ArrayList<>();
        private List<String> issues = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class PrivateCommitResponse {
        private String draftId;
        private Integer importedCount;
        private List<Long> questionIds = new ArrayList<>();
    }
}
