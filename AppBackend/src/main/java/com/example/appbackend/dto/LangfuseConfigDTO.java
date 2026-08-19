package com.example.appbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class LangfuseConfigDTO {

    @Data
    public static class TestResultVO {
        private Boolean success;
        private String detail;
        private String target;
    }

    @Data
    public static class ConfigVO {
        private Boolean enabled;
        private String baseUrl;
        private Boolean publicKeyConfigured;
        private Boolean secretKeyConfigured;
        private String publicKeyMasked;
        private String secretKeyMasked;
        private String updateTime;
    }

    @Data
    public static class UpdateRequest {
        @NotNull(message = "启用状态不能为空")
        private Boolean enabled;
        private String baseUrl;
        /** Empty values preserve an already stored credential. */
        private String publicKey;
        private String secretKey;
    }
}
