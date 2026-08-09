package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MindMapDTO;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MindMapAIServiceImplTest {

    @Test
    void localFallbackUsesStructureTypeForDifferentTopLevelBranches() {
        MindMapAIServiceImpl service = localFallbackService();

        List<String> courseNodes = topLevelNames(service, "大学计算机课程", "课程体系");
        List<String> reviewNodes = topLevelNames(service, "大学计算机课程", "复习提纲");
        List<String> projectNodes = topLevelNames(service, "大学计算机课程", "项目拆解");
        List<String> knowledgeNodes = topLevelNames(service, "大学计算机课程", "知识梳理");

        Assertions.assertTrue(courseNodes.contains("基础课程"));
        Assertions.assertTrue(reviewNodes.contains("核心知识"));
        Assertions.assertTrue(projectNodes.contains("项目目标"));
        Assertions.assertTrue(knowledgeNodes.contains("概念定义"));
        Assertions.assertNotEquals(courseNodes, reviewNodes);
        Assertions.assertNotEquals(courseNodes, projectNodes);
        Assertions.assertNotEquals(reviewNodes, knowledgeNodes);
    }

    @Test
    void autoStructureInfersCourseSystemFromCourseInput() {
        MindMapAIServiceImpl service = localFallbackService();

        List<String> nodes = topLevelNames(service, "生成一份大学计算机课程体系的思维导图", "自动");

        Assertions.assertTrue(nodes.contains("基础课程"));
        Assertions.assertTrue(nodes.contains("核心课程"));
    }

    @Test
    void userDefinedCenterTopicIsHardConstraint() {
        MindMapAIServiceImpl service = localFallbackService();

        MindMapDTO.MindMapData data = service.generate(
                "请根据文件整理课程体系",
                "Java 并发编程",
                "USER_DEFINED",
                "3",
                "KNOWLEDGE",
                "STANDARD",
                null
        );

        Assertions.assertEquals("Java 并发编程", data.getTitle());
        Assertions.assertEquals("USER_DEFINED", data.getRequestedCenterTopicMode());
        Assertions.assertEquals("Java 并发编程", data.getResolvedCenterTopic());
    }

    @Test
    void manualDepthCapsMaximumLevelWithoutForcingEveryBranch() {
        MindMapAIServiceImpl service = localFallbackService();

        MindMapDTO.MindMapData data = service.generate(
                "生成一份大学计算机课程体系的思维导图",
                "",
                "AUTO",
                "2",
                "COURSE",
                "DETAILED",
                null
        );

        Assertions.assertEquals("2", data.getRequestedDepth());
        Assertions.assertEquals(2, data.getResolvedDepth());
        Assertions.assertTrue(maxLevel(data.getNodes(), 1) <= 2);
    }

    @Test
    void detailedDoesNotIncreaseManualDepth() {
        MindMapAIServiceImpl service = localFallbackService();

        MindMapDTO.MindMapData data = service.generate(
                "生成一份项目拆解思维导图，包含目标、任务和交付物",
                "",
                "AUTO",
                "3",
                "PROJECT",
                "DETAILED",
                null
        );

        Assertions.assertEquals("DETAILED", data.getDetailLevel());
        Assertions.assertEquals(3, data.getResolvedDepth());
        Assertions.assertTrue(maxLevel(data.getNodes(), 1) <= 3);
    }

    @Test
    void autoSettingsSaveResolvedSnapshotFields() {
        MindMapAIServiceImpl service = localFallbackService();

        MindMapDTO.MindMapData data = service.generate(
                "请把大学计算机课程体系整理成思维导图",
                "",
                "AUTO",
                "AUTO",
                "AUTO",
                "STANDARD",
                null
        );

        Assertions.assertEquals("AUTO", data.getRequestedDepth());
        Assertions.assertNotNull(data.getResolvedDepth());
        Assertions.assertEquals("AUTO", data.getRequestedStructure());
        Assertions.assertEquals("COURSE", data.getResolvedStructure());
    }

    private MindMapAIServiceImpl localFallbackService() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);

        when(systemConfigService.getValue(anyString(), anyString())).thenReturn("");
        when(systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1))
                .thenReturn(List.of());

        return new MindMapAIServiceImpl(systemConfigService, systemConfigRepository, new ObjectMapper());
    }

    private List<String> topLevelNames(MindMapAIServiceImpl service, String inputText, String structure) {
        MindMapDTO.MindMapData data = service.generate(inputText, "", "AUTO", "3", structure, "standard", null);
        return data.getNodes().stream()
                .map(MindMapDTO.Node::getName)
                .toList();
    }

    private int maxLevel(List<MindMapDTO.Node> nodes, int level) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        return nodes.stream()
                .mapToInt(node -> Math.max(level, maxLevel(node.getChildren(), level + 1)))
                .max()
                .orElse(level);
    }
}
