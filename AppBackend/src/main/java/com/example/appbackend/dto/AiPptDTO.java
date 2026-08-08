package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

public final class AiPptDTO {
    private AiPptDTO() {
    }

    @Data
    public static class OptionsResponse {
        private List<SceneOption> scenes;
        private long cacheTtlSeconds;
    }

    @Data
    public static class SceneOption {
        private String value;
        private String label;
        private String description;
        private boolean enabled;
        @JsonProperty("default")
        private boolean defaultOption;
    }

    @Data
    public static class OutlineRequest {
        @NotBlank
        @Size(max = 255)
        private String sourceName;
        @NotBlank
        @Size(max = 200000)
        private String sourceContent;
        @Size(max = 32)
        private String outlineMode = "ai_outline";
        @Min(3)
        @Max(50)
        private Integer pageCount = 15;
        @Size(max = 32)
        private String scene = "review";
        @Size(max = 200)
        private String topic;
    }

    @Data
    public static class SlidesRequest {
        @NotNull
        private Map<String, Object> outline;
        @Size(max = 200000)
        private String sourceContent;
        private Map<String, Object> settings;
        @Size(max = 2000)
        private String sharedPrompt;
    }

    @Data
    public static class TaskRequest {
        @NotBlank
        @Size(max = 255)
        private String sourceName;
        @NotNull
        private Map<String, Object> outline;
        @NotEmpty
        @Size(min = 2, max = 50)
        private List<Map<String, Object>> slides;
        @Size(max = 2000)
        private String sharedPrompt;
        private Map<String, Object> settings;
        @Size(max = 2)
        private List<String> exportFormats;
    }
}
