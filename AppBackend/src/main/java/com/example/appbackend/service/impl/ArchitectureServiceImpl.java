package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ArchitectureDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ArchitectureRecord;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.entity.Result;
import com.example.appbackend.repository.ArchitectureRecordRepository;
import com.example.appbackend.service.ArchitectureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 架构图生成服务实现。
 * 负责：调用 Python AI 服务 → 解析返回 JSON → 落库 → 返回前端。
 */
@Service
public class ArchitectureServiceImpl implements ArchitectureService {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureServiceImpl.class);

    private final PythonAiProxyService pythonAiProxyService;
    private final ArchitectureRecordRepository architectureRecordRepository;
    private final ObjectMapper objectMapper;

    public ArchitectureServiceImpl(PythonAiProxyService pythonAiProxyService,
                                   ArchitectureRecordRepository architectureRecordRepository,
                                   ObjectMapper objectMapper) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.architectureRecordRepository = architectureRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ArchitectureDTO.GenerateResponse generate(ArchitectureDTO.GenerateRequest request, Long userId, String authorization) {
        if (request == null || !StringUtils.hasText(request.getDescription())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "需求描述不能为空");
        }
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }

        // 构造转发给 Python 的请求体（保持 camelCase 字段名与 Python 端 Pydantic 模型一致）
        Map<String, Object> pythonRequest = new LinkedHashMap<>();
        pythonRequest.put("description", request.getDescription().trim());
        pythonRequest.put("systemType", request.getSystemType() == null ? "" : request.getSystemType());
        pythonRequest.put("architectureStyle", request.getArchitectureStyle() == null ? "" : request.getArchitectureStyle());
        pythonRequest.put("layers", request.getLayers() == null ? List.of() : request.getLayers());
        pythonRequest.put("displayContent", request.getDisplayContent() == null ? List.of() : request.getDisplayContent());
        pythonRequest.put("relationType", request.getRelationType() == null ? "" : request.getRelationType());

        Object rawResponse = pythonAiProxyService.generateArchitecture(pythonRequest, authorization);
        Map<String, Object> archData = parseArchitectureData(rawResponse);

        // 落库
        ArchitectureRecord record = new ArchitectureRecord();
        record.setUserId(userId);
        record.setTitle(getString(archData, "title", "AI 架构图"));
        record.setDescription(request.getDescription().trim());
        record.setSystemType(pythonRequest.get("systemType").toString());
        record.setArchitectureStyle(pythonRequest.get("architectureStyle").toString());
        record.setConfigJson(writeJson(Map.of(
                "layers", pythonRequest.get("layers"),
                "displayContent", pythonRequest.get("displayContent"),
                "relationType", pythonRequest.get("relationType")
        )));
        record.setArchitectureJson(writeJson(archData));
        ArchitectureRecord saved = architectureRecordRepository.save(record);
        log.info("架构图生成并落库成功 recordId={} userId={} title={}", saved.getId(), userId, saved.getTitle());

        return toGenerateResponse(saved, archData);
    }

    @Override
    public PageResponse<ArchitectureDTO.HistoryItem> history(Long userId, Integer page, Integer size) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        int pageNum = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ArchitectureRecord> records = architectureRecordRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable);

        List<ArchitectureDTO.HistoryItem> items = new ArrayList<>();
        for (ArchitectureRecord record : records.getContent()) {
            items.add(new ArchitectureDTO.HistoryItem(
                    record.getId(),
                    record.getTitle(),
                    record.getSystemType(),
                    record.getCreateTime() == null ? null : record.getCreateTime().toString()
            ));
        }
        return new PageResponse<>(items, records.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public ArchitectureDTO.GenerateResponse detail(Long id, Long userId) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        if (id == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "记录ID不能为空");
        }
        ArchitectureRecord record = architectureRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "架构图记录不存在或无权访问"));

        Map<String, Object> archData = parseJson(record.getArchitectureJson());
        return toGenerateResponse(record, archData);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArchitectureData(Object rawResponse) {
        if (rawResponse == null) {
            throw new BusinessException(Result.ERROR_CODE, "架构图生成服务返回空数据");
        }
        if (rawResponse instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        // 如果 Python 返回的是 String，尝试 JSON 解析
        if (rawResponse instanceof String str) {
            return parseJson(str);
        }
        throw new BusinessException(Result.ERROR_CODE, "架构图生成服务返回格式异常");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception e) {
            log.warn("解析架构 JSON 失败: {}", e.getMessage());
        }
        return Map.of();
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("序列化架构 JSON 失败: {}", e.getMessage());
            return "";
        }
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        String str = value.toString().trim();
        return str.isEmpty() ? defaultValue : str;
    }

    @SuppressWarnings("unchecked")
    private ArchitectureDTO.GenerateResponse toGenerateResponse(ArchitectureRecord record, Map<String, Object> archData) {
        List<Object> nodes = archData.get("nodes") instanceof List<?> list ? (List<Object>) list : List.of();
        List<Object> edges = archData.get("edges") instanceof List<?> list2 ? (List<Object>) list2 : List.of();
        List<Object> layers = archData.get("layers") instanceof List<?> list3 ? (List<Object>) list3 : List.of();
        List<Object> thirdParty = archData.get("thirdParty") instanceof List<?> list4 ? (List<Object>) list4 : List.of();
        List<String> features = new ArrayList<>();
        Object rawFeatures = archData.get("features");
        if (rawFeatures instanceof List<?> fList) {
            for (Object f : fList) {
                if (f != null) features.add(String.valueOf(f));
            }
        }
        return new ArchitectureDTO.GenerateResponse(
                record.getId(),
                record.getTitle(),
                getString(archData, "style", ""),
                getString(archData, "subtitle", ""),
                layers,
                thirdParty,
                features,
                nodes,
                edges,
                record.getCreateTime() == null ? null : record.getCreateTime().toString()
        );
    }
}
