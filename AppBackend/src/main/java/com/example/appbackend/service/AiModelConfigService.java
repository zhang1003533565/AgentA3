package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.AiModelConfig;
import com.example.appbackend.entity.AgentModelBind;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiModelConfigRepository;
import com.example.appbackend.repository.AgentModelBindRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * AI Model Config Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiModelConfigRepository aiModelConfigRepository;
    private final AgentModelBindRepository agentModelBindRepository;

    /**
     * 创建新的模型配置
     */
    @Transactional
    public AiModelConfigDTO.ConfigVO createConfig(AiModelConfigDTO.CreateRequest request) {
        // 检查 provider 是否已存在
        List<AiModelConfig> configs = aiModelConfigRepository.findAll();
        for (AiModelConfig config : configs) {
            if (config.getProvider() != null && config.getProvider().equalsIgnoreCase(request.getProvider())) {
                throw new BusinessException(400, "该供应商配置已存在");
            }
        }

        AiModelConfig config = new AiModelConfig();
        config.setProvider(request.getProvider());
        config.setBaseUrl(request.getBaseUrl());
        config.setApiKey(request.getApiKey());
        config.setModelName(request.getModelName());
        config.setStatus(request.getStatus());

        AiModelConfig saved = aiModelConfigRepository.save(config);
        return AiModelConfigDTO.ConfigVO.fromEntity(saved);
    }

    /**
     * 更新模型配置
     */
    @Transactional
    public AiModelConfigDTO.ConfigVO updateConfig(AiModelConfigDTO.UpdateRequest request) {
        AiModelConfig config = aiModelConfigRepository.findById(request.getId())
                .orElseThrow(() -> new BusinessException(404, "配置不存在"));

        config.setProvider(request.getProvider());
        config.setBaseUrl(request.getBaseUrl());
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            config.setApiKey(request.getApiKey());
        }
        config.setModelName(request.getModelName());
        config.setStatus(request.getStatus());

        AiModelConfig saved = aiModelConfigRepository.save(config);
        return AiModelConfigDTO.ConfigVO.fromEntity(saved);
    }

    /**
     * 获取所有模型配置
     */
    public List<AiModelConfigDTO.ConfigVO> getAllConfigs() {
        return aiModelConfigRepository.findAll().stream()
                .map(AiModelConfigDTO.ConfigVO::fromEntity)
                .toList();
    }

    /**
     * 根据 ID 获取配置
     */
    public AiModelConfigDTO.ConfigVO getConfigById(Long id) {
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "配置不存在"));
        return AiModelConfigDTO.ConfigVO.fromEntity(config);
    }

    /**
     * 获取启用的配置列表
     */
    public List<AiModelConfigDTO.ConfigVO> getEnabledConfigs() {
        return aiModelConfigRepository.findAll().stream()
                .filter(config -> config.getStatus() != null && config.getStatus() == 1)
                .map(AiModelConfigDTO.ConfigVO::fromEntity)
                .toList();
    }
}
