package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImageGenerationDTO {

    @Data
    @Schema(description = "AI image generation request")
    public static class GenerateRequest {
        @Schema(description = "Image prompt", example = "Campus smart navigation system")
        private String prompt;

        @Schema(description = "Optional visual style", example = "clean vector")
        private String style;

        @Schema(description = "Image size", example = "1328x1328")
        private String size;

        @Schema(description = "Image count", example = "1")
        private Integer count;

        @Schema(description = "Random seed", example = "12345")
        private Integer seed;

        @Schema(description = "Negative prompt")
        private String negativePrompt;

        @Schema(description = "Return type", allowableValues = {"url", "base64", "url_and_base64"}, example = "url")
        private String returnType;

        @Schema(description = "Whether to enable prompt extension", example = "true")
        private Boolean promptExtend;

        @Schema(description = "Whether to add watermark", example = "false")
        private Boolean watermark;

        @Schema(description = "Model ID", example = "qwen-image-plus")
        private String model;

        @Schema(description = "Provider name", example = "qwen")
        private String provider;

        @Schema(description = "Provider base URL", example = "https://dashscope.aliyuncs.com")
        private String baseUrl;

        @Schema(description = "Provider API key")
        private String apiKey;

        @Schema(
                description = "Optional diagram type. Leave empty for normal image generation.",
                allowableValues = {
                        "mindmap", "flowchart", "activity", "architecture", "sequence",
                        "usecase", "class", "er", "gantt", "chart", "network",
                        "org", "state", "journey", "concept"
                },
                example = "flowchart"
        )
        private String chartType;

        @Schema(description = "Extra metadata")
        private Map<String, Object> metadata;

        @Schema(description = "Batch prompts, used by /batch")
        private List<String> prompts;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new LinkedHashMap<>();

        @JsonAnySetter
        public void setAdditionalProperty(String name, Object value) {
            additionalProperties.put(name, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> body = new LinkedHashMap<>(additionalProperties);
            putIfNotNull(body, "prompt", prompt);
            putIfNotNull(body, "style", style);
            putIfNotNull(body, "size", size);
            putIfNotNull(body, "count", count);
            putIfNotNull(body, "seed", seed);
            putIfNotNull(body, "negativePrompt", negativePrompt);
            putIfNotNull(body, "returnType", returnType);
            putIfNotNull(body, "promptExtend", promptExtend);
            putIfNotNull(body, "watermark", watermark);
            putIfNotNull(body, "model", model);
            putIfNotNull(body, "provider", provider);
            putIfNotNull(body, "baseUrl", baseUrl);
            putIfNotNull(body, "apiKey", apiKey);
            putIfNotNull(body, "chartType", chartType);
            putIfNotNull(body, "metadata", metadata);
            putIfNotNull(body, "prompts", prompts);
            return body;
        }

        private void putIfNotNull(Map<String, Object> body, String key, Object value) {
            if (value != null) {
                body.put(key, value);
            }
        }
    }
}
