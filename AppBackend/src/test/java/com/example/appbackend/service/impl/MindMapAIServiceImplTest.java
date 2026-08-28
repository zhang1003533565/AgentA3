package com.example.appbackend.service.impl;

import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MindMapAIServiceImplTest {

    @Test
    void throwsWhenMindMapAgentModelIsNotConfigured() {
        MindMapAIServiceImpl service = serviceWithoutBinding();

        BusinessException error = Assertions.assertThrows(
                BusinessException.class,
                () -> service.generate("生成思维导图", "", "AUTO", "AUTO", "AUTO", "STANDARD", null)
        );

        Assertions.assertTrue(error.getMessage().contains("diagram_mind_map_agent"));
    }

    @Test
    void rejectsNonTextModelBoundToMindMapAgent() {
        MindMapAIServiceImpl service = serviceWithBinding("ai.service.image.wan");

        BusinessException error = Assertions.assertThrows(
                BusinessException.class,
                () -> service.generate("原电池证据推理问题链", "", "AUTO", "AUTO", "AUTO", "STANDARD", null)
        );

        Assertions.assertTrue(error.getMessage().contains("diagram_mind_map_agent"));
    }

    @Test
    void extractsContentFromNestedOutputChoicesResponse() throws Exception {
        MindMapAIServiceImpl service = serviceWithoutBinding();
        JsonNode response = new ObjectMapper().readTree("""
                {
                  "output": {
                    "choices": [{
                      "message": {
                        "content": [{"type": "text", "text": "{\\"title\\":\\"测试\\",\\"nodes\\":[]}"}]
                      }
                    }]
                  }
                }
                """);

        String content = ReflectionTestUtils.invokeMethod(service, "extractResponseContent", response);

        Assertions.assertEquals("{\"title\":\"测试\",\"nodes\":[]}", content);
    }

    @Test
    void extractsContentFromUnknownNestedEnvelope() throws Exception {
        MindMapAIServiceImpl service = serviceWithoutBinding();
        JsonNode response = new ObjectMapper().readTree("""
                {
                  "result": {
                    "responses": [{
                      "payload": {
                        "message": {
                          "content": [{"text": "{\\"title\\":\\"测试\\",\\"nodes\\":[]}"}]
                        }
                      }
                    }]
                  }
                }
                """);

        String content = ReflectionTestUtils.invokeMethod(service, "extractResponseContent", response);

        Assertions.assertEquals("{\"title\":\"测试\",\"nodes\":[]}", content);
    }

    private MindMapAIServiceImpl serviceWithoutBinding() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);
        when(systemConfigService.getValue(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1))
                .thenReturn(java.util.List.of());
        return new MindMapAIServiceImpl(systemConfigService, systemConfigRepository, new ObjectMapper());
    }

    private MindMapAIServiceImpl serviceWithBinding(String configPrefix) {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);
        when(systemConfigService.getValue(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if ("ai.agent-bindings.diagram_mind_map_agent.model".equals(key)) {
                return configPrefix;
            }
            return invocation.getArgument(1);
        });
        when(systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1))
                .thenReturn(java.util.List.of());
        return new MindMapAIServiceImpl(systemConfigService, systemConfigRepository, new ObjectMapper());
    }
}
