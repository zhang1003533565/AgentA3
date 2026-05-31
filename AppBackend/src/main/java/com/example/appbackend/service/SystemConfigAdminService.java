package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.SystemConfigDTO;

public interface SystemConfigAdminService {

    PageResponse<SystemConfigDTO.ConfigVO> list(Integer current, Integer size, String keyword, String group, String prefixes);

    void update(Long id, SystemConfigDTO.UpdateRequest req);

    void delete(Long id);

    SystemConfigDTO.ConfigVO upsert(SystemConfigDTO.UpsertRequest req);

    SystemConfigDTO.TestResultVO test(Long id);

    SystemConfigDTO.TestResultVO testAiModel(SystemConfigDTO.AiModelTestRequest req, String authorization);
}
