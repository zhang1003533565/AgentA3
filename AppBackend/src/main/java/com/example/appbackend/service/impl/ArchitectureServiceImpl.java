package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ArchitectureDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ArchitectureRecord;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.entity.Result;
import com.example.appbackend.repository.ArchitectureRecordRepository;
import com.example.appbackend.service.ArchitectureService;
import com.example.appbackend.service.FileParseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AI 架构图生成服务实现。
 * 负责：调用 Python AI 服务 → 解析返回 JSON → 落库 → 返回前端。
 */
@Service
public class ArchitectureServiceImpl implements ArchitectureService {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureServiceImpl.class);

    private final PythonAiProxyService pythonAiProxyService;
    private final ArchitectureRecordRepository architectureRecordRepository;
    private final FileParseService fileParseService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String fileBaseUrl;

    public ArchitectureServiceImpl(PythonAiProxyService pythonAiProxyService,
                                   ArchitectureRecordRepository architectureRecordRepository,
                                   FileParseService fileParseService,
                                   ObjectMapper objectMapper) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.architectureRecordRepository = architectureRecordRepository;
        this.fileParseService = fileParseService;
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

        // 文档解析文本优先：有 sourceText 时拼进 description 传给 AI（与流程图一致）
        String description = request.getDescription().trim();
        String sourceText = request.getSourceText() == null ? "" : request.getSourceText().trim();
        String inputDescription = StringUtils.hasText(sourceText)
                ? (description + "\n\n【文档内容】\n" + sourceText)
                : description;

        List<String> architectureLayers = firstNonEmptyList(request.getArchitectureLayers(), request.getLayers());
        boolean autoArchitectureLayers = request.getAutoArchitectureLayers() == null
                ? architectureLayers.isEmpty()
                : request.getAutoArchitectureLayers();
        if (autoArchitectureLayers) {
            architectureLayers = List.of();
        }
        List<String> focusContents = firstNonEmptyList(request.getFocusContents(), request.getDisplayContent());
        String systemType = firstText(request.getSystemType(), "WEB");
        String architectureStyle = firstText(request.getArchitectureStyle(), "AUTO");
        String requestedRelationMode = normalizeRelationMode(firstText(request.getRelationMode(), request.getRelationType(), "AUTO"));

        // 构造转发给 Python 的请求体（保持 camelCase 字段名与 Python 端 Pydantic 模型一致）
        Map<String, Object> pythonRequest = new LinkedHashMap<>();
        pythonRequest.put("description", inputDescription);
        pythonRequest.put("systemType", systemType);
        pythonRequest.put("architectureStyle", architectureStyle);
        pythonRequest.put("autoArchitectureLayers", autoArchitectureLayers);
        pythonRequest.put("architectureLayers", architectureLayers);
        pythonRequest.put("layers", architectureLayers);
        pythonRequest.put("focusContents", focusContents);
        pythonRequest.put("displayContent", focusContents);
        pythonRequest.put("relationMode", requestedRelationMode);
        pythonRequest.put("relationType", requestedRelationMode);

        Object rawResponse = pythonAiProxyService.generateArchitecture(pythonRequest, authorization);
        Map<String, Object> archData = new LinkedHashMap<>(parseArchitectureData(rawResponse));
        String resolvedRelationMode = "AUTO".equals(requestedRelationMode)
                ? normalizeRelationMode(getString(archData, "resolvedRelationMode", getString(archData, "relationMode", "MODULE")))
                : requestedRelationMode;
        if ("AUTO".equals(resolvedRelationMode)) {
            resolvedRelationMode = "MODULE";
        }
        archData.put("systemType", systemType);
        archData.put("autoArchitectureLayers", autoArchitectureLayers);
        archData.put("architectureLayers", architectureLayers);
        archData.put("focusContents", focusContents);
        archData.put("requestedRelationMode", requestedRelationMode);
        archData.put("resolvedRelationMode", resolvedRelationMode);
        archData.put("relationMode", resolvedRelationMode);

        // 落库
        ArchitectureRecord record = new ArchitectureRecord();
        record.setUserId(userId);
        record.setTitle(getString(archData, "title", "AI 架构图"));
        record.setDescription(request.getDescription().trim());
        record.setSystemType(systemType);
        record.setArchitectureStyle(architectureStyle);
        record.setConfigJson(writeJson(Map.of(
                "autoArchitectureLayers", autoArchitectureLayers,
                "architectureLayers", architectureLayers,
                "layers", architectureLayers,
                "focusContents", focusContents,
                "displayContent", focusContents,
                "requestedRelationMode", requestedRelationMode,
                "resolvedRelationMode", resolvedRelationMode,
                "relationMode", requestedRelationMode,
                "relationType", requestedRelationMode,
                "systemType", systemType
        )));
        record.setArchitectureJson(writeJson(archData));
        ArchitectureRecord saved = architectureRecordRepository.save(record);
        log.info("架构图生成并落库成功 recordId={} userId={} title={}", saved.getId(), userId, saved.getTitle());

        return toGenerateResponse(saved, archData);
    }

    @Override
    public ArchitectureDTO.UploadResponse uploadAndParse(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        validateUpload(file);
        String originalName = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "architecture-file");
        String extension = extensionOf(originalName);
        String fileId = UUID.randomUUID().toString();
        String objectKey = "architecture/" + LocalDate.now() + "/" + fileId + extension;
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(objectKey).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BusinessException(400, "上传路径不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (Exception error) {
            throw new BusinessException(500, "文件保存失败: " + error.getMessage());
        }

        ArchitectureDTO.UploadResponse response = new ArchitectureDTO.UploadResponse();
        response.setFileId(fileId);
        response.setFileName(originalName);
        response.setSourceFile(buildFileUrl(objectKey));
        response.setText(fileParseService.parse(target.toFile()));
        return response;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        if (!List.of(".pdf", ".doc", ".docx", ".ppt", ".pptx", ".md", ".markdown")
                .contains(extensionOf(file.getOriginalFilename()))) {
            throw new BusinessException(400, "仅支持 pdf、doc、docx、ppt、pptx、md");
        }
    }

    private String buildFileUrl(String objectKey) {
        String normalizedBaseUrl = StringUtils.hasText(fileBaseUrl)
                ? fileBaseUrl.trim().replaceAll("/+$", "") : "";
        return normalizedBaseUrl + "/uploads/" + objectKey.replace('\\', '/');
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
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
            Map<String, Object> config = parseJson(record.getConfigJson());
            Map<String, Object> archData = parseJson(record.getArchitectureJson());
            String requestedRelationMode = normalizeRelationMode(firstText(
                    getString(config, "requestedRelationMode", ""),
                    getString(config, "relationMode", ""),
                    getString(config, "relationType", ""),
                    getString(archData, "requestedRelationMode", ""),
                    "AUTO"
            ));
            String resolvedRelationMode = normalizeRelationMode(firstText(
                    getString(archData, "resolvedRelationMode", ""),
                    getString(config, "resolvedRelationMode", ""),
                    "AUTO".equals(requestedRelationMode) ? "MODULE" : requestedRelationMode
            ));
            items.add(new ArchitectureDTO.HistoryItem(
                    record.getId(),
                    record.getTitle(),
                    record.getSystemType(),
                    requestedRelationMode,
                    resolvedRelationMode,
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

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeRelationMode(String value) {
        String text = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "AUTO";
        if ("DATA".equals(text) || "DATAFLOW".equals(text)) {
            return "DATA_FLOW";
        }
        if ("CALL_CHAIN".equals(text) || "CALLING".equals(text) || "DEPENDENCY".equals(text)) {
            return "CALL";
        }
        if (List.of("AUTO", "MODULE", "DATA_FLOW", "CALL").contains(text)) {
            return text;
        }
        return "AUTO";
    }

    private List<String> firstNonEmptyList(List<String> preferred, List<String> fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        if (fallback != null && !fallback.isEmpty()) {
            return fallback;
        }
        return List.of();
    }

    private List<String> objectToStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private ArchitectureDTO.GenerateResponse toGenerateResponse(ArchitectureRecord record, Map<String, Object> archData) {
        Map<String, Object> config = parseJson(record.getConfigJson());
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
        String requestedRelationMode = normalizeRelationMode(firstText(
                getString(archData, "requestedRelationMode", ""),
                getString(config, "requestedRelationMode", ""),
                getString(config, "relationMode", ""),
                getString(config, "relationType", ""),
                "AUTO"
        ));
        String resolvedRelationMode = normalizeRelationMode(firstText(
                getString(archData, "resolvedRelationMode", ""),
                getString(config, "resolvedRelationMode", ""),
                "AUTO".equals(requestedRelationMode) ? getString(archData, "relationMode", "MODULE") : requestedRelationMode
        ));
        if ("AUTO".equals(resolvedRelationMode)) {
            resolvedRelationMode = "MODULE";
        }
        List<String> architectureLayers = objectToStringList(archData.get("architectureLayers"));
        if (architectureLayers.isEmpty()) {
            architectureLayers = objectToStringList(config.get("architectureLayers"));
        }
        if (architectureLayers.isEmpty()) {
            architectureLayers = objectToStringList(config.get("layers"));
        }
        List<String> focusContents = objectToStringList(archData.get("focusContents"));
        if (focusContents.isEmpty()) {
            focusContents = objectToStringList(config.get("focusContents"));
        }
        if (focusContents.isEmpty()) {
            focusContents = objectToStringList(config.get("displayContent"));
        }
        return new ArchitectureDTO.GenerateResponse(
                record.getId(),
                record.getTitle(),
                getString(archData, "style", ""),
                getString(archData, "subtitle", ""),
                layers,
                thirdParty,
                features,
                firstText(getString(archData, "systemType", ""), getString(config, "systemType", ""), record.getSystemType(), "WEB"),
                getBoolean(config, "autoArchitectureLayers", architectureLayers.isEmpty()),
                architectureLayers,
                focusContents,
                requestedRelationMode,
                resolvedRelationMode,
                nodes,
                edges,
                record.getCreateTime() == null ? null : record.getCreateTime().toString()
        );
    }
}
