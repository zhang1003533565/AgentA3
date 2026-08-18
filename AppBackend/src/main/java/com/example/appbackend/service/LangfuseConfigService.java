package com.example.appbackend.service;

import com.example.appbackend.dto.LangfuseConfigDTO;
import org.springframework.http.HttpHeaders;

public interface LangfuseConfigService {
    LangfuseConfigDTO.ConfigVO getConfig();

    LangfuseConfigDTO.ConfigVO updateConfig(LangfuseConfigDTO.UpdateRequest request);

    LangfuseConfigDTO.TestResultVO testConfig();

    void applyPythonHeaders(HttpHeaders headers);
}
