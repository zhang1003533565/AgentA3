package com.example.appbackend.service;

import com.example.appbackend.dto.AgentModelBindDTO;
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
 * Agent Model Bind Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentModelBindService {

    private final AgentModelBindRepository agentModelBindRepository;
    private final AiModelConfigRepository aiModelConfigRepository;

    /**
     * 绑定智能体到模型配置
     * 一个智能体只能绑定一套模型（唯一约束）
     */
    @Transactional
    public AgentModelBindDTO.BindVO bindAgent(AgentModelBindDTO.BindRequest request) {
        // 验证 model_config_id 是否存在且启用
        AiModelConfig config = aiModelConfigRepository.findById(request.getModelConfigId())
                .orElseThrow(() -> new BusinessException(404, "模型配置不存在"));
        
        if (config.getStatus() != null && config.getStatus() != 1) {
            throw new BusinessException(400, "模型配置未启用，无法绑定");
        }

        // 检查该智能体是否已有绑定（手动查询，不使用自定义命名方法）
        List<AgentModelBind> allBinds = agentModelBindRepository.findAll();
        AgentModelBind existing = allBinds.stream()
                .filter(b -> b.getAgentId().equals(request.getAgentId()))
                .findFirst()
                .orElse(null);
        
        if (existing != null) {
            // 如果是更新操作，允许修改
            if (!existing.getModelConfigId().equals(request.getModelConfigId())) {
                throw new BusinessException(400, "该智能体已绑定其他模型配置，请先解除绑定或更新现有绑定");
            }
            return AgentModelBindDTO.BindVO.fromEntity(existing);
        }

        // 创建新的绑定
        AgentModelBind bind = new AgentModelBind();
        bind.setAgentId(request.getAgentId());
        bind.setModelConfigId(request.getModelConfigId());

        AgentModelBind saved = agentModelBindRepository.save(bind);
        return AgentModelBindDTO.BindVO.fromEntity(saved);
    }

    /**
     * 根据 agentId 获取绑定信息和完整模型配置
     */
    public Map<String, Object> getBindInfoByAgent(String agentId) {
        // 手动查询，不使用自定义命名方法
        List<AgentModelBind> allBinds = agentModelBindRepository.findAll();
        AgentModelBind bind = allBinds.stream()
                .filter(b -> b.getAgentId().equals(agentId))
                .findFirst()
                .orElse(null);
        
        if (bind == null) {
            throw new BusinessException(404, "未找到该智能体的模型绑定配置");
        }
        
        // 获取模型配置详情（包含解密后的 API Key）
        AiModelConfig config = aiModelConfigRepository.findById(bind.getModelConfigId())
                .orElseThrow(() -> new BusinessException(404, "模型配置不存在"));

        // 构建响应
        AgentModelBindDTO.BindVO bindVO = AgentModelBindDTO.BindVO.fromEntity(bind);
        AgentModelBindDTO.ModelConfigDetail configDetail = new AgentModelBindDTO.ModelConfigDetail();
        configDetail.setId(config.getId());
        configDetail.setProvider(config.getProvider());
        configDetail.setBaseUrl(config.getBaseUrl());
        configDetail.setApiKey(config.getApiKey()); // Java 服务内部可以使用明文密钥
        configDetail.setModelName(config.getModelName());
        configDetail.setStatus(config.getStatus());

        return Map.of(
                "bindInfo", bindVO,
                "modelConfig", configDetail
        );
    }

    /**
     * 解除智能体绑定
     */
    @Transactional
    public void unbindAgent(String agentId) {
        // 手动查找并删除
        List<AgentModelBind> allBinds = agentModelBindRepository.findAll();
        AgentModelBind bind = allBinds.stream()
                .filter(b -> b.getAgentId().equals(agentId))
                .findFirst()
                .orElse(null);
        
        if (bind == null) {
            throw new BusinessException(404, "该智能体未绑定模型配置");
        }
        
        agentModelBindRepository.delete(bind);
        log.info("已解除智能体 {} 的模型绑定", agentId);
    }

    /**
     * 更新绑定（如果已存在则更新）
     */
    @Transactional
    public AgentModelBindDTO.BindVO updateBind(AgentModelBindDTO.BindRequest request) {
        return bindAgent(request);
    }
}
