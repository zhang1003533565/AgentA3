package com.example.appbackend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QuestionGenerationDTO {

    private QuestionGenerationDTO() {
    }

    public record ParsedMaterial(String text, String originalFilename, String sourceTitle) {
    }

    @Data
    public static class GenerationResponse {
        private String questionType;
        private String agentName;
        private String agentRole;
        private String sourceTitle;
        private String originalFilename;
        private Integer maxQuestions;
        private Integer generatedCount;
        private List<Map<String, Object>> questions = new ArrayList<>();
        private List<String> missingInfo = new ArrayList<>();
        private Boolean valid;
        private List<String> issues = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }
}
