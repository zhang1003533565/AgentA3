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
}
