package com.example.appbackend.service.impl;

import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.FileSummaryResult;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileSummaryServiceImplTest {

    @Test
    void summarizeFallsBackToLocalSummaryWhenAiConfigMissing() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);
        when(systemConfigService.getValue(anyString(), anyString())).thenReturn("");
        when(systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)).thenReturn(List.of());

        FileSummaryServiceImpl service = new FileSummaryServiceImpl(
                systemConfigService,
                systemConfigRepository,
                new ObjectMapper()
        );

        FileSummaryResult result = service.summarize(
                "计算机课程体系.pdf",
                "计算机课程体系包含程序设计、数据结构、操作系统、数据库和课程实验。"
        );

        Assertions.assertEquals("LOCAL", result.status());
        Assertions.assertEquals("local", result.model());
        Assertions.assertTrue(result.summary().contains("计算机课程体系.pdf"));
        Assertions.assertTrue(result.summary().contains("程序设计"));
    }
}
