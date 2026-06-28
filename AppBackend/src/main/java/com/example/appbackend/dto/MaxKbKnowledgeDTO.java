package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class MaxKbKnowledgeDTO {

    @Data
    @Schema(description = "MaxKB 账号创建请求")
    public static class AccountCreateRequest {
        @NotBlank(message = "账号名称不能为空")
        private String accountName;

        @NotBlank(message = "MaxKB 服务地址不能为空")
        private String baseUrl;

        @Schema(description = "环境：local/test/prod/custom", example = "test")
        private String environment = "local";

        @NotBlank(message = "MaxKB OpenAPI Key 不能为空")
        private String apiKey;

        @NotBlank(message = "MaxKB 工作空间 ID 不能为空")
        private String workspaceId;

        private String remark;

        @NotNull(message = "状态不能为空")
        private Integer status = 1;
    }

    @Data
    @Schema(description = "MaxKB 账号更新请求")
    public static class AccountUpdateRequest {
        @NotBlank(message = "账号名称不能为空")
        private String accountName;

        @NotBlank(message = "MaxKB 服务地址不能为空")
        private String baseUrl;

        @Schema(description = "环境：local/test/prod/custom", example = "prod")
        private String environment = "local";

        @Schema(description = "MaxKB OpenAPI Key；为空时保留原密钥")
        private String apiKey;

        @NotBlank(message = "MaxKB 工作空间 ID 不能为空")
        private String workspaceId;

        private String remark;

        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    @Schema(description = "MaxKB 账号状态请求")
    public static class AccountStatusRequest {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    @Schema(description = "MaxKB 账号响应")
    public static class AccountVO {
        private Long id;
        private String accountName;
        private String baseUrl;
        private String environment;
        private String environmentText;
        private String workspaceId;
        private String remark;
        private Integer status;
        private String statusText;
        private Boolean apiKeyConfigured;
        private String apiKeyMasked;
        private String createTime;
        private String updateTime;
    }

    @Data
    @Schema(description = "MaxKB 环境选项")
    public static class EnvironmentOption {
        private String value;
        private String label;
        private String description;

        public EnvironmentOption(String value, String label, String description) {
            this.value = value;
            this.label = label;
            this.description = description;
        }
    }
}
