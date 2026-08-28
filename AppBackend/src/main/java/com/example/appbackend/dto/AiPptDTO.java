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
        private List<TemplateOption> templates;
        private long cacheTtlSeconds;
        private String engine;
        private boolean enhancedEngineAvailable;
        private boolean editorEnabled;
        private boolean templateCatalogAvailable = true;
    }

    @Data
    public static class TemplateOption {
        private String id;
        private String name;
        private String description;
        private String thumbnailUrl;
        private Integer layoutCount;
        private List<LayoutInfo> layouts;
        @JsonProperty("default")
        private boolean defaultOption;
    }

    @Data
    public static class LayoutInfo {
        private String id;
        private String description;
        private List<String> elementTypes;
        private List<String> slots;
        private List<String> previewTexts;
    }

    @Data
    public static class OutlineRequest {
        @NotBlank
        @Size(max = 255)
        private String sourceName;
        @Size(max = 200000)
        private String sourceContent;
        @Size(max = 80)
        private String sourceFileId;
        @Size(max = 32)
        private String outlineMode = "ai_outline";
        @Min(3)
        @Max(50)
        private Integer pageCount = 30;
        @Size(max = 200000)
        private String sourceSupplement;
        @Size(max = 200)
        private String topic;
        @Size(max = 64)
        private String audience;
        @Size(max = 32)
        private String tone;
    }

    @Data
    public static class SlidesRequest {
        @NotNull
        private Map<String, Object> outline;
        @Size(max = 200000)
        private String sourceContent;
        @Size(max = 200000)
        private String sourceSupplement;
        @Size(max = 80)
        private String sourceFileId;
        private Map<String, Object> settings;
        @Size(max = 2000)
        private String sharedPrompt;
        @Size(max = 64)
        private String audience;
        @Size(max = 32)
        private String tone;
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
        @Size(max = 1)
        private List<String> exportFormats;
    }

    @Data
    public static class SlideImageRequest {
        @NotBlank
        @Size(max = 12000000)
        private String imageBase64;
        @Size(max = 8)
        private String extension = "png";
    }

    @Data
    public static class PreviewRequest {
        @Size(max = 64)
        private String templateId = "general";
        @Size(max = 255)
        private String title = "演示文稿";
        @NotNull
        private Map<String, Object> slide;
        private Map<String, Object> settings;
    }
}
