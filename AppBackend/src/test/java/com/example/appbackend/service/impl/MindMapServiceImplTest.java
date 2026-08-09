package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MindMapDTO;
import com.example.appbackend.entity.MindMapRecord;
import com.example.appbackend.repository.MindMapRecordRepository;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.FileParseService;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MindMapServiceImplTest {

    @Test
    void generateFallsBackToLocalStructuredMindMapWhenAiConfigMissing() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);
        MindMapRecordRepository recordRepository = mock(MindMapRecordRepository.class);
        FileParseService fileParseService = mock(FileParseService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(systemConfigService.getValue(anyString(), anyString())).thenReturn("");
        when(systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)).thenReturn(List.of());
        when(recordRepository.save(any(MindMapRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MindMapAIServiceImpl aiService = new MindMapAIServiceImpl(
                systemConfigService,
                systemConfigRepository,
                objectMapper
        );
        MindMapServiceImpl service = new MindMapServiceImpl(
                aiService,
                fileParseService,
                recordRepository,
                objectMapper
        );

        MindMapDTO.GenerateRequest request = new MindMapDTO.GenerateRequest();
        request.setTopic("生成一份Linux学习路线的思维导图");
        request.setDepth("3");
        request.setStructure("知识梳理");
        request.setDetail("standard");

        MindMapDTO.GenerateResponse response = service.generate(1L, request, null);

        Assertions.assertNotNull(response.getId());
        Assertions.assertEquals("Linux学习路线", response.getTitle());
        Assertions.assertFalse(response.getNodes().isEmpty());
        Assertions.assertEquals("Linux 基础", response.getNodes().get(0).getName());
        Assertions.assertFalse(response.getNodes().get(0).getChildren().isEmpty());
        verify(recordRepository).save(any(MindMapRecord.class));
    }

    @Test
    void generateKeepsTextAndFileContentTogetherWhenBothProvided() {
        com.example.appbackend.service.MindMapAIService aiService = mock(com.example.appbackend.service.MindMapAIService.class);
        MindMapRecordRepository recordRepository = mock(MindMapRecordRepository.class);
        FileParseService fileParseService = mock(FileParseService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        MindMapDTO.MindMapData data = new MindMapDTO.MindMapData();
        data.setTitle("计算机课程体系");
        data.setNodes(List.of(node("基础课程")));

        when(aiService.generate(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(data);
        when(recordRepository.save(any(MindMapRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MindMapServiceImpl service = new MindMapServiceImpl(aiService, fileParseService, recordRepository, objectMapper);

        MindMapDTO.GenerateRequest request = new MindMapDTO.GenerateRequest();
        request.setTopic("请重点整理实验实践部分");
        request.setCenterTopic("计算机课程体系");
        request.setSourceText("课程包含程序设计、数据结构、操作系统和课程实验。");
        request.setDepth("3");
        request.setStructure("课程体系");
        request.setDetail("standard");

        service.generate(1L, request, null);

        org.mockito.ArgumentCaptor<String> inputCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(aiService).generate(
                inputCaptor.capture(),
                eq("计算机课程体系"),
                eq("3"),
                eq("课程体系"),
                eq("standard"),
                eq(null)
        );
        Assertions.assertTrue(inputCaptor.getValue().contains("请重点整理实验实践部分"));
        Assertions.assertTrue(inputCaptor.getValue().contains("课程包含程序设计"));
    }

    private MindMapDTO.Node node(String name) {
        MindMapDTO.Node node = new MindMapDTO.Node();
        node.setName(name);
        return node;
    }
}
