package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AiWriteDTO {

    @Data
    public static class WriteRequest {
        @NotBlank(message = "写作需求不能为空")
        private String prompt;

        private String tone;

        private String wordCount;

        private String modelName;
    }

    @Data
    public static class WriteResponse {
        private String content;
        private String model;
    }

    @Data
    public static class ModelOption {
        private String configPrefix;
        private String provider;
        private String providerName;
        private String model;
        private String displayName;
        private boolean tested;
    }

    @Data
    public static class ExportRequest {
        @NotBlank(message = "标题不能为空")
        private String title;

        private String sceneLabel;

        private String generatedAt;

        private String model;

        @NotBlank(message = "正文不能为空")
        private String content;
    }
}
