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
    }

    @Data
    @Schema(description = "配置连通测试响应")
    public static class TestResultVO {
        private Long id;
        private String configKey;
        private Boolean success;
        private String target;
        private String detail;
    }
}
