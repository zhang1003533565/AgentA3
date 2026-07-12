package com.example.appbackend.service.impl;

import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.QuestionTypeOption;
import com.example.appbackend.service.QuestionGenerationService;
import com.example.appbackend.service.SystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class QuestionGenerationServiceImpl implements QuestionGenerationService {

    private static final String MAPPING_PREFIX = "ai.question-generation.agent.";
    private static final List<String> QUESTION_TYPES = List.of(
            "single_choice", "multiple_choice", "true_false", "fill_blank", "short_answer");

    private final SystemConfigService systemConfigService;
    private final PythonAiProxyService pythonAiProxyService;

    public QuestionGenerationServiceImpl(SystemConfigService systemConfigService,
                                         PythonAiProxyService pythonAiProxyService) {
        this.systemConfigService = systemConfigService;
        this.pythonAiProxyService = pythonAiProxyService;
    }

    @Override
    public OptionsResponse getOptions(String authorization) {
        Map<String, PythonAiProxyService.AgentDescriptor> catalog =
                pythonAiProxyService.getQuestionGenerationAgentCatalog(authorization);
        OptionsResponse response = new OptionsResponse();
        response.setQuestionTypes(QUESTION_TYPES.stream()
                .map(type -> resolveOption(type, catalog))
                .toList());
        return response;
    }

    private QuestionTypeOption resolveOption(
            String type,
            Map<String, PythonAiProxyService.AgentDescriptor> catalog
    ) {
        QuestionTypeOption option = new QuestionTypeOption();
        option.setType(type);
        String agentName = systemConfigService.getValue(MAPPING_PREFIX + type, "");
        if (!StringUtils.hasText(agentName)) {
            unavailable(option, "未配置题型智能体");
            return option;
        }

        agentName = agentName.trim();
        option.setAgentName(agentName);
        PythonAiProxyService.AgentDescriptor descriptor = catalog.get(agentName);
        if (descriptor == null) {
            unavailable(option, "配置的智能体不存在");
        } else if (!descriptor.enabled()) {
            option.setAgentRole(descriptor.role());
            unavailable(option, "配置的智能体已停用");
        } else if (!StringUtils.hasText(descriptor.modelBinding())) {
            option.setAgentRole(descriptor.role());
            unavailable(option, "配置的智能体未绑定已测试模型");
        } else {
            option.setAgentRole(descriptor.role());
            option.setAvailable(true);
            option.setUnavailableReason(null);
        }
        return option;
    }

    private void unavailable(QuestionTypeOption option, String reason) {
        option.setAvailable(false);
        option.setUnavailableReason(reason);
    }
}
