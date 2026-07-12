package com.example.appbackend.service.impl;

import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.QuestionTypeOption;
import com.example.appbackend.service.SystemConfigService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionGenerationServiceImplTest {

    private static final String AUTHORIZATION = "Bearer test-token";

    private final Map<String, String> config = new HashMap<>();
    private Map<String, PythonAiProxyService.AgentDescriptor> agents = Map.of();

    @Test
    void returnsFiveQuestionTypesAndMarksValidMappingAvailable() {
        map("single_choice", "choice_agent");
        catalog(Map.of("choice_agent", descriptor("choice_agent", "选择题专家", true, "ai.service.text.choice")));

        OptionsResponse response = service().getOptions(AUTHORIZATION);

        assertThat(response.getQuestionTypes()).extracting(QuestionTypeOption::getType)
                .containsExactly("single_choice", "multiple_choice", "true_false", "fill_blank", "short_answer");
        assertThat(option(response, "single_choice"))
                .extracting(QuestionTypeOption::getAgentName, QuestionTypeOption::getAgentRole,
                        QuestionTypeOption::getAvailable, QuestionTypeOption::getUnavailableReason)
                .containsExactly("choice_agent", "选择题专家", true, null);
    }

    @Test
    void marksMissingMappingUnavailableWithoutFallingBackToAnotherAgent() {
        catalog(Map.of("some_agent", descriptor("some_agent", "任意专家", true, "ai.service.text.some")));

        QuestionTypeOption option = option(service().getOptions(AUTHORIZATION), "multiple_choice");

        assertThat(option.getAvailable()).isFalse();
        assertThat(option.getAgentName()).isNull();
        assertThat(option.getUnavailableReason()).contains("未配置题型智能体");
    }

    @Test
    void marksMappingToUnknownAgentUnavailable() {
        map("true_false", "missing_agent");
        catalog(Map.of());

        QuestionTypeOption option = option(service().getOptions(AUTHORIZATION), "true_false");

        assertThat(option.getAvailable()).isFalse();
        assertThat(option.getAgentName()).isEqualTo("missing_agent");
        assertThat(option.getUnavailableReason()).contains("智能体不存在");
    }

    @Test
    void marksDisabledAgentUnavailable() {
        map("fill_blank", "blank_agent");
        catalog(Map.of("blank_agent", descriptor("blank_agent", "填空题专家", false, "ai.service.text.blank")));

        QuestionTypeOption option = option(service().getOptions(AUTHORIZATION), "fill_blank");

        assertThat(option.getAvailable()).isFalse();
        assertThat(option.getUnavailableReason()).contains("智能体已停用");
    }

    @Test
    void marksAgentWithoutTestedModelBindingUnavailable() {
        map("short_answer", "short_agent");
        catalog(Map.of("short_agent", descriptor("short_agent", "简答题专家", true, null)));

        QuestionTypeOption option = option(service().getOptions(AUTHORIZATION), "short_answer");

        assertThat(option.getAvailable()).isFalse();
        assertThat(option.getUnavailableReason()).contains("未绑定已测试模型");
    }

    private QuestionGenerationServiceImpl service() {
        SystemConfigService systemConfigService = new SystemConfigService() {
            @Override
            public String getValue(String key, String defaultValue) {
                return config.getOrDefault(key, defaultValue);
            }

            @Override
            public Long getLongValue(String key, Long defaultValue) {
                return defaultValue;
            }

            @Override
            public Boolean getBooleanValue(String key, Boolean defaultValue) {
                return defaultValue;
            }
        };
        PythonAiProxyService pythonAiProxyService = new PythonAiProxyService(
                null, null, null, systemConfigService, null, "http://localhost", 1, 1024) {
            @Override
            public Map<String, AgentDescriptor> getQuestionGenerationAgentCatalog(String authorization) {
                return agents;
            }
        };
        return new QuestionGenerationServiceImpl(systemConfigService, pythonAiProxyService);
    }

    private void map(String type, String agentName) {
        config.put("ai.question-generation.agent." + type, agentName);
    }

    private void catalog(Map<String, PythonAiProxyService.AgentDescriptor> catalog) {
        agents = catalog;
    }

    private PythonAiProxyService.AgentDescriptor descriptor(String name, String role, boolean enabled, String modelBinding) {
        return new PythonAiProxyService.AgentDescriptor(name, role, enabled, modelBinding);
    }

    private QuestionTypeOption option(OptionsResponse response, String type) {
        return response.getQuestionTypes().stream()
                .filter(candidate -> type.equals(candidate.getType()))
                .findFirst()
                .orElseThrow();
    }
}
