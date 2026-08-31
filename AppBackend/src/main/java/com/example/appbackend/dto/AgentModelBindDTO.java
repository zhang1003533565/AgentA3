package com.example.appbackend.dto;

import lombok.Data;

/**
 * Agent Model Bind DTO
 */
public class AgentModelBindDTO {

    public static class BindRequest {
        private String agentId;
        private Long modelConfigId;

        // Getters and Setters
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        
        public Long getModelConfigId() { return modelConfigId; }
        public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
    }

    public static class BindVO {
        private Long id;
        private String agentId;
        private Long modelConfigId;
        private java.time.LocalDateTime createTime;
        private java.time.LocalDateTime updateTime;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        
        public Long getModelConfigId() { return modelConfigId; }
        public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
        
        public java.time.LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
        
        public java.time.LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }

        public static BindVO fromEntity(com.example.appbackend.entity.AgentModelBind entity) {
            BindVO vo = new BindVO();
            vo.setId(entity.getId());
            vo.setAgentId(entity.getAgentId());
            vo.setModelConfigId(entity.getModelConfigId());
            vo.setCreateTime(entity.getCreateTime());
            vo.setUpdateTime(entity.getUpdateTime());
            return vo;
        }
    }

    public static class ModelConfigDetail {
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

    public static class CompleteResponse {
        private BindVO bindInfo;
        private ModelConfigDetail modelConfig;

        // Getters and Setters
        public BindVO getBindInfo() { return bindInfo; }
        public void setBindInfo(BindVO bindInfo) { this.bindInfo = bindInfo; }
        
        public ModelConfigDetail getModelConfig() { return modelConfig; }
        public void setModelConfig(ModelConfigDetail modelConfig) { this.modelConfig = modelConfig; }

        public static CompleteResponse from(BindVO bind, ModelConfigDetail config) {
            CompleteResponse resp = new CompleteResponse();
            resp.setBindInfo(bind);
            resp.setModelConfig(config);
            return resp;
        }
    }
}
