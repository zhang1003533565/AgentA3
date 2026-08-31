package com.example.appbackend.dto;

import lombok.Data;

/**
 * AI Model Config DTO
 */
public class AiModelConfigDTO {

    public static class CreateRequest {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer status = 1;

        // Getters and Setters
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class UpdateRequest {
        private Long id;
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer status;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class ConfigVO {
        private Long id;
        private String provider;
        private String baseUrl;
        private String apiKeyMasked;
        private String modelName;
        private Integer status;
        private java.time.LocalDateTime createTime;
        private java.time.LocalDateTime updateTime;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public String getApiKeyMasked() { return apiKeyMasked; }
        public void setApiKeyMasked(String apiKeyMasked) { this.apiKeyMasked = apiKeyMasked; }
        
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        
        public java.time.LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
        
        public java.time.LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }

        public static ConfigVO fromEntity(com.example.appbackend.entity.AiModelConfig entity) {
            ConfigVO vo = new ConfigVO();
            vo.setId(entity.getId());
            vo.setProvider(entity.getProvider());
            vo.setBaseUrl(entity.getBaseUrl());
            vo.setApiKeyMasked(maskApiKey(entity.getApiKey()));
            vo.setModelName(entity.getModelName());
            vo.setStatus(entity.getStatus());
            vo.setCreateTime(entity.getCreateTime());
            vo.setUpdateTime(entity.getUpdateTime());
            return vo;
        }

        private static String maskApiKey(String apiKey) {
            if (apiKey == null || apiKey.length() <= 8) {
                return "*****";
            }
            return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
        }
    }
}
