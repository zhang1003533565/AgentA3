package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class SystemConfigDTO {

    @Data
    @Schema(description = "系统配置响应")
    public static class ConfigVO {
        private Long id;
        private String configKey;
        private String configValue;
        private String configGroup;
        private String description;
        private Integer status;
        private String statusText;
        private Boolean testable;
        private Integer isDefault;
        private String updateTime;
    }

    @Data
    @Schema(description = "系统配置更新请求")
    public static class UpdateRequest {
        @NotBlank(message = "配置值不能为空")
        private String configValue;

        private String description;

        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    @Schema(description = "系统配置按键保存请求")
    public static class UpsertRequest {
        @NotBlank(message = "配置键不能为空")
        private String configKey;

        @NotBlank(message = "配置值不能为空")
        private String configValue;

        private String configGroup = "ai";

        private String description;

        @NotNull(message = "状态不能为空")
        private Integer status;

        private Integer isDefault = 0;
    }

    @Data
    @Schema(description = "AI 模型临时测试请求")
    public static class AiModelTestRequest {
        @NotBlank(message = "能力类型不能为空")
        private String modality;

        @NotBlank(message = "服务商不能为空")
        private String provider;

        @NotBlank(message = "Base URL 不能为空")
        private String baseUrl;

        @NotBlank(message = "API Key 不能为空")
        private String apiKey;

        @NotBlank(message = "模型 ID 不能为空")
        private String model;

        private String prompt;

        @Schema(description = "视觉测试媒体类型：image/video", example = "image")
        private String mediaType;

        @Schema(description = "视觉测试媒体 URL（可选）", example = "https://example.com/demo.jpg")
        private String mediaUrl;

        @Schema(description = "视觉测试媒体 Base64（不含 data: 前缀）")
        private String mediaBase64;

        @Schema(description = "视觉测试媒体 MIME 类型", example = "image/jpeg")
        private String mediaMimeType;

        @Schema(description = "视觉测试媒体文件名", example = "demo.jpg")
        private String mediaFilename;
    }

    @Data
    @Schema(description = "配置连通测试响应")
    public static class TestResultVO {
        private Long id;
        private String configKey;
        private Boolean success;
        private String target;
        private String detail;
        private String provider;
        private String model;
        private String modality;
        private String prompt;
        private Object raw;
    }
}
