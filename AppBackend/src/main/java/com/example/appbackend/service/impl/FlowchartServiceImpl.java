package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FlowchartDTO;
import com.example.appbackend.entity.FlowchartRecord;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FlowchartRecordRepository;
import com.example.appbackend.service.FileParseService;
import com.example.appbackend.service.FileSummaryResult;
import com.example.appbackend.service.FileSummaryService;
import com.example.appbackend.service.FlowchartAIService;
import com.example.appbackend.service.FlowchartService;
import com.example.appbackend.service.ParsedFileContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FlowchartServiceImpl implements FlowchartService {
    private final FlowchartAIService flowchartAIService;
    private final FileParseService fileParseService;
    private final FileSummaryService fileSummaryService;
    private final FlowchartRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String fileBaseUrl;

    public FlowchartServiceImpl(FlowchartAIService flowchartAIService,
                                FileParseService fileParseService,
                                FileSummaryService fileSummaryService,
                                FlowchartRecordRepository recordRepository,
                                ObjectMapper objectMapper) {
        this.flowchartAIService = flowchartAIService;
        this.fileParseService = fileParseService;
        this.fileSummaryService = fileSummaryService;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public FlowchartDTO.GenerateResponse generate(Long userId, FlowchartDTO.GenerateRequest request, String authorization) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        String description = firstText(request.getContent(), request.getDescription());
        String sourceText = trim(request.getSourceText());
        String inputText = combineInput(description, sourceText);
        if (!StringUtils.hasText(inputText)) {
            throw new BusinessException(400, "请输入流程描述或上传可解析文件");
        }
        String sceneType = normalizeSceneType(firstText(request.getSceneType(), request.getProcessType()));
        String nodeGranularity = normalizeNodeGranularity(firstText(request.getNodeGranularity(), request.getNodeLevel()));
        String requestedLayoutDirection = normalizeLayoutDirection(request.getLayoutDirection());
        String requestedDecisionMode = normalizeDecisionMode(request.getDecisionMode());
        String requestedSwimlaneMode = normalizeSwimlaneMode(firstText(request.getSwimlaneMode(), request.getSwimlane()));

        request.setDescription(description);
        request.setContent(description);
        request.setSceneType(sceneType);
        request.setProcessType(sceneType);
        request.setNodeGranularity(nodeGranularity);
        request.setNodeLevel(nodeGranularity);
        request.setLayoutDirection(requestedLayoutDirection);
        request.setDecisionMode(requestedDecisionMode);
        request.setSwimlaneMode(requestedSwimlaneMode);
        request.setSwimlane(requestedSwimlaneMode);

        FlowchartDTO.FlowchartData data = flowchartAIService.generate(request, inputText, authorization);
        completeResultMetadata(data, sceneType, nodeGranularity, requestedLayoutDirection,
                requestedDecisionMode, requestedSwimlaneMode);
        FlowchartRecord record = new FlowchartRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setTitle(data.getTitle());
        record.setDescription(inputText);
        record.setProcessType(data.getSceneType());
        record.setDiagramType(data.getType());
        record.setConfigJson(writeJson(request));
        record.setFlowJson(writeJson(data));
        recordRepository.save(record);
        return toResponse(record, data);
    }

    @Override
    public FlowchartDTO.UploadResponse uploadAndParse(Long userId, MultipartFile file) {
        validateUpload(file);
        String originalName = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "flowchart-file");
        String extension = extensionOf(originalName);
        String fileId = UUID.randomUUID().toString();
        String objectKey = "flowchart/" + LocalDate.now() + "/" + fileId + extension;
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

        FlowchartDTO.UploadResponse response = new FlowchartDTO.UploadResponse();
        ParsedFileContent parsed = fileParseService.parseDetailed(target.toFile());
        FileSummaryResult summary = fileSummaryService.summarize(originalName, parsed.text());
        response.setFileId(fileId);
        response.setFileName(originalName);
        response.setSourceFile(buildFileUrl(objectKey));
        response.setText(parsed.text());
        response.setSummary(summary.summary());
        response.setSummaryStatus(summary.status());
        response.setSummaryModel(summary.model());
        response.setTextLength(parsed.textLength());
        response.setTruncated(parsed.truncated());
        response.setPageCount(parsed.pageCount());
        response.setSlideCount(parsed.slideCount());
        response.setParagraphCount(parsed.paragraphCount());
        return response;
    }

    @Override
    public List<FlowchartDTO.HistoryItem> history(Long userId) {
        return recordRepository.findTop20ByUserIdOrderByCreateTimeDesc(userId)
                .stream()
                .map(this::toHistoryItem)
                .toList();
    }

    @Override
    public FlowchartDTO.GenerateResponse detail(Long userId, String id) {
        FlowchartRecord record = recordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(404, "流程图记录不存在"));
        try {
            FlowchartDTO.FlowchartData data = objectMapper.readValue(record.getFlowJson(), FlowchartDTO.FlowchartData.class);
            completeResultMetadata(data, record.getProcessType(), data.getNodeGranularity(),
                    data.getRequestedLayoutDirection(), data.getRequestedDecisionMode(), data.getRequestedSwimlaneMode());
            return toResponse(record, data);
        } catch (Exception error) {
            throw new BusinessException(500, "流程图记录数据损坏");
        }
    }

    @Override
    public void delete(Long userId, String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(400, "记录ID不能为空");
        }
        FlowchartRecord record = recordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(404, "流程图记录不存在"));
        recordRepository.delete(record);
    }

    private FlowchartDTO.GenerateResponse toResponse(FlowchartRecord record, FlowchartDTO.FlowchartData data) {
        Map<String, Object> config = parseJson(record.getConfigJson());
        FlowchartDTO.GenerateResponse response = new FlowchartDTO.GenerateResponse();
        response.setId(record.getId());
        response.setTitle(data.getTitle());
        response.setType(data.getType());
        response.setSceneType(data.getSceneType());
        response.setNodeGranularity(data.getNodeGranularity());
        response.setRequestedLayoutDirection(data.getRequestedLayoutDirection());
        response.setResolvedLayoutDirection(data.getResolvedLayoutDirection());
        response.setRequestedDecisionMode(data.getRequestedDecisionMode());
        response.setResolvedDecisionMode(data.getResolvedDecisionMode());
        response.setRequestedSwimlaneMode(data.getRequestedSwimlaneMode());
        response.setResolvedSwimlaneMode(data.getResolvedSwimlaneMode());
        response.setLanes(data.getLanes());
        response.setNodes(data.getNodes());
        response.setEdges(data.getEdges());
        response.setCreateTime(record.getCreateTime());
        response.setDescription(record.getDescription());
        response.setContent(firstText(getString(config, "content", ""), getString(config, "description", ""), record.getDescription()));
        response.setFiles(objectToFileRefs(config.get("files")));
        response.setSourceText(getString(config, "sourceText", ""));
        response.setFileId(getString(config, "fileId", ""));
        response.setSourceFile(getString(config, "sourceFile", ""));
        response.setFileSummary(firstText(getString(config, "fileSummary", ""), firstFileSummary(response.getFiles())));
        return response;
    }

    private FlowchartDTO.HistoryItem toHistoryItem(FlowchartRecord record) {
        Map<String, Object> config = parseJson(record.getConfigJson());
        FlowchartDTO.HistoryItem item = new FlowchartDTO.HistoryItem();
        item.setId(record.getId());
        item.setTitle(record.getTitle());
        item.setCreateTime(record.getCreateTime());
        item.setType(record.getDiagramType());
        item.setDescription(record.getDescription());
        item.setContent(firstText(getString(config, "content", ""), getString(config, "description", ""), record.getDescription()));
        item.setFiles(objectToFileRefs(config.get("files")));
        item.setSourceText(getString(config, "sourceText", ""));
        item.setFileId(getString(config, "fileId", ""));
        item.setSourceFile(getString(config, "sourceFile", ""));
        item.setFileSummary(firstText(getString(config, "fileSummary", ""), firstFileSummary(item.getFiles())));
        try {
            FlowchartDTO.FlowchartData data = objectMapper.readValue(record.getFlowJson(), FlowchartDTO.FlowchartData.class);
            completeResultMetadata(data, record.getProcessType(), data.getNodeGranularity(),
                    data.getRequestedLayoutDirection(), data.getRequestedDecisionMode(), data.getRequestedSwimlaneMode());
            item.setSceneType(data.getSceneType());
            item.setNodeGranularity(data.getNodeGranularity());
            item.setRequestedLayoutDirection(data.getRequestedLayoutDirection());
            item.setResolvedLayoutDirection(data.getResolvedLayoutDirection());
            item.setRequestedDecisionMode(data.getRequestedDecisionMode());
            item.setResolvedDecisionMode(data.getResolvedDecisionMode());
            item.setRequestedSwimlaneMode(data.getRequestedSwimlaneMode());
            item.setResolvedSwimlaneMode(data.getResolvedSwimlaneMode());
        } catch (Exception ignored) {
            item.setSceneType(record.getProcessType());
            item.setRequestedLayoutDirection("VERTICAL");
            item.setResolvedLayoutDirection("VERTICAL");
        }
        return item;
    }

    private void completeResultMetadata(FlowchartDTO.FlowchartData data,
                                        String sceneType,
                                        String nodeGranularity,
                                        String requestedLayoutDirection,
                                        String requestedDecisionMode,
                                        String requestedSwimlaneMode) {
        if (data == null) {
            return;
        }
        data.setSceneType(normalizeResolvedSceneType(firstText(data.getSceneType(), sceneType)));
        data.setNodeGranularity(normalizeNodeGranularity(firstText(data.getNodeGranularity(), nodeGranularity)));
        data.setRequestedLayoutDirection(normalizeLayoutDirection(firstText(
                data.getRequestedLayoutDirection(), requestedLayoutDirection)));
        data.setResolvedLayoutDirection(normalizeLayoutDirection(firstText(
                data.getResolvedLayoutDirection(), data.getRequestedLayoutDirection())));
        data.setRequestedDecisionMode(normalizeDecisionMode(firstText(data.getRequestedDecisionMode(), requestedDecisionMode)));
        data.setRequestedSwimlaneMode(normalizeSwimlaneMode(firstText(data.getRequestedSwimlaneMode(), requestedSwimlaneMode)));
        normalizeNodes(data);
        normalizeLanes(data);
        normalizeEdges(data);
        if (!StringUtils.hasText(data.getResolvedDecisionMode())) {
            boolean hasDecision = data.getNodes() != null && data.getNodes().stream()
                    .anyMatch(node -> "decision".equalsIgnoreCase(node.getType()));
            data.setResolvedDecisionMode(hasDecision ? "ENABLED" : "DISABLED");
        } else {
            data.setResolvedDecisionMode(normalizeResolvedDecisionMode(data.getResolvedDecisionMode()));
        }
        if ("NONE".equals(data.getRequestedSwimlaneMode())) {
            data.setLanes(new ArrayList<>());
            data.getNodes().forEach(node -> {
                node.setLaneId(null);
                node.setLane(null);
            });
            data.setResolvedSwimlaneMode("NONE");
        } else if (!StringUtils.hasText(data.getResolvedSwimlaneMode())) {
            data.setResolvedSwimlaneMode(resolveSwimlaneMode(data));
        } else {
            data.setResolvedSwimlaneMode(normalizeSwimlaneMode(data.getResolvedSwimlaneMode()));
        }
        if ("NONE".equals(data.getResolvedSwimlaneMode())) {
            data.setType("FLOWCHART");
        } else {
            data.setType("SWIMLANE");
        }
    }

    private void normalizeNodes(FlowchartDTO.FlowchartData data) {
        if (data.getNodes() == null) {
            data.setNodes(new ArrayList<>());
        }
        for (FlowchartDTO.Node node : data.getNodes()) {
            if (!StringUtils.hasText(node.getName())) {
                node.setName(firstText(node.getLabel(), node.getDescription(), "流程步骤"));
            }
            if (!StringUtils.hasText(node.getLabel())) {
                node.setLabel(node.getName());
            }
            node.setType(normalizeNodeType(node.getType()));
            if ("NONE".equals(data.getRequestedDecisionMode()) && "decision".equals(node.getType())) {
                node.setType("process");
            }
            if (!StringUtils.hasText(node.getLaneId()) && StringUtils.hasText(node.getLane())) {
                node.setLaneId(slug(node.getLane()));
            }
        }
    }

    private void normalizeLanes(FlowchartDTO.FlowchartData data) {
        if (data.getLanes() == null) {
            data.setLanes(new ArrayList<>());
        }
        String requested = data.getRequestedSwimlaneMode();
        String laneType = "DEPARTMENT".equals(requested) ? "department" : "role";
        for (FlowchartDTO.Lane lane : data.getLanes()) {
            if (!StringUtils.hasText(lane.getLabel())) {
                lane.setLabel(firstText(lane.getName(), lane.getId(), "泳道"));
            }
            if (!StringUtils.hasText(lane.getName())) {
                lane.setName(lane.getLabel());
            }
            if (!StringUtils.hasText(lane.getId())) {
                lane.setId(slug(lane.getLabel()));
            }
            if (!StringUtils.hasText(lane.getType())) {
                lane.setType(laneType);
            }
        }
        Map<String, FlowchartDTO.Lane> laneByAlias = new HashMap<>();
        for (FlowchartDTO.Lane lane : data.getLanes()) {
            addLaneAlias(laneByAlias, lane.getId(), lane);
            addLaneAlias(laneByAlias, lane.getLabel(), lane);
            addLaneAlias(laneByAlias, lane.getName(), lane);
            addLaneAlias(laneByAlias, slug(lane.getLabel()), lane);
            addLaneAlias(laneByAlias, slug(lane.getName()), lane);
        }
        for (FlowchartDTO.Node node : data.getNodes()) {
            FlowchartDTO.Lane lane = laneByAlias.get(firstText(node.getLaneId(), node.getLane()));
            if (lane != null) {
                node.setLaneId(lane.getId());
                node.setLane(lane.getLabel());
            }
        }
        if ("ROLE".equals(requested) || "DEPARTMENT".equals(requested)) {
            ensureRequiredLane(data, laneType);
        }
    }

    private void addLaneAlias(Map<String, FlowchartDTO.Lane> laneByAlias, String alias, FlowchartDTO.Lane lane) {
        if (StringUtils.hasText(alias)) {
            laneByAlias.put(alias, lane);
        }
    }

    private void ensureRequiredLane(FlowchartDTO.FlowchartData data, String laneType) {
        Set<String> existing = new LinkedHashSet<>();
        data.getLanes().forEach(lane -> {
            existing.add(firstText(lane.getId(), slug(lane.getLabel())));
            existing.add(slug(lane.getLabel()));
            existing.add(slug(lane.getName()));
        });
        for (FlowchartDTO.Node node : data.getNodes()) {
            String laneLabel = firstText(node.getLaneId(), node.getLane());
            if (StringUtils.hasText(laneLabel)
                    && !existing.contains(laneLabel)
                    && !existing.contains(slug(laneLabel))) {
                FlowchartDTO.Lane lane = new FlowchartDTO.Lane();
                lane.setId(slug(laneLabel));
                lane.setLabel(laneLabel);
                lane.setName(laneLabel);
                lane.setType(laneType);
                data.getLanes().add(lane);
                existing.add(lane.getId());
            }
        }
        if (data.getLanes().isEmpty()) {
            FlowchartDTO.Lane lane = new FlowchartDTO.Lane();
            lane.setId("main");
            lane.setLabel("department".equals(laneType) ? "责任部门" : "主要参与者");
            lane.setName(lane.getLabel());
            lane.setType(laneType);
            data.getLanes().add(lane);
        }
        String fallbackLaneId = data.getLanes().get(0).getId();
        String fallbackLaneName = data.getLanes().get(0).getLabel();
        data.getNodes().forEach(node -> {
            if (!StringUtils.hasText(node.getLaneId())) {
                node.setLaneId(fallbackLaneId);
                node.setLane(fallbackLaneName);
            }
        });
    }

    private void normalizeEdges(FlowchartDTO.FlowchartData data) {
        if (data.getEdges() == null) {
            data.setEdges(new ArrayList<>());
        }
        for (int index = 0; index < data.getEdges().size(); index += 1) {
            FlowchartDTO.Edge edge = data.getEdges().get(index);
            if (!StringUtils.hasText(edge.getId())) {
                edge.setId("e" + (index + 1));
            }
            if (!StringUtils.hasText(edge.getType())) {
                edge.setType(StringUtils.hasText(edge.getLabel()) || StringUtils.hasText(edge.getCondition())
                        ? "branch" : "normal");
            }
        }
    }

    private String combineInput(String description, String sourceText) {
        if (StringUtils.hasText(description) && StringUtils.hasText(sourceText)) {
            return description + "\n\n文件解析内容：\n" + sourceText;
        }
        return StringUtils.hasText(sourceText) ? sourceText : description;
    }

    private String normalizeSceneType(String value) {
        String text = firstText(value, "ADMIN").toUpperCase(Locale.ROOT);
        if (text.contains("AUTO") || text.contains("自动")) return "AUTO";
        if (text.contains("BUSINESS") || text.contains("业务")) return "BUSINESS";
        if (text.contains("LEARNING") || text.contains("STUDY") || text.contains("学习")) return "LEARNING";
        if (text.contains("LIFE") || text.contains("生活")) return "LIFE";
        return "ADMIN";
    }

    private String normalizeResolvedSceneType(String value) {
        String text = normalizeSceneType(value);
        return "AUTO".equals(text) ? "ADMIN" : text;
    }

    private String normalizeNodeGranularity(String value) {
        String text = firstText(value, "AUTO").toUpperCase(Locale.ROOT);
        if (text.contains("SIMPLE") || text.contains("简")) return "SIMPLE";
        if (text.contains("DETAIL") || text.contains("详细")) return "DETAILED";
        if (text.contains("STANDARD") || text.contains("标准")) return "STANDARD";
        return "AUTO";
    }

    private String normalizeLayoutDirection(String value) {
        String text = firstText(value, "VERTICAL").toUpperCase(Locale.ROOT);
        if (text.contains("HORIZONTAL") || text.contains("LANDSCAPE") || text.contains("横")) return "HORIZONTAL";
        return "VERTICAL";
    }

    private String normalizeDecisionMode(String value) {
        String text = firstText(value, "AUTO").toUpperCase(Locale.ROOT);
        if (text.contains("FORCE") || text.contains("INCLUDE") || text.contains("强制")) return "FORCE";
        if (text.contains("NONE") || text.contains("LINEAR") || text.contains("不使用") || text.contains("不包含")) return "NONE";
        return "AUTO";
    }

    private String normalizeResolvedDecisionMode(String value) {
        String text = firstText(value, "DISABLED").toUpperCase(Locale.ROOT);
        return text.contains("ENABLE") || text.contains("TRUE") || text.contains("YES") ? "ENABLED" : "DISABLED";
    }

    private String normalizeSwimlaneMode(String value) {
        String text = firstText(value, "AUTO").toUpperCase(Locale.ROOT);
        if (text.contains("DEPARTMENT") || text.contains("部门")) return "DEPARTMENT";
        if (text.contains("ROLE") || text.contains("角色")) return "ROLE";
        if (text.contains("NONE") || text.contains("HIDDEN") || text.contains("不显示")) return "NONE";
        return "AUTO";
    }

    private String resolveSwimlaneMode(FlowchartDTO.FlowchartData data) {
        if (data.getLanes() == null || data.getLanes().isEmpty()) {
            return "NONE";
        }
        boolean department = data.getLanes().stream().anyMatch(lane -> "department".equalsIgnoreCase(lane.getType()));
        return department ? "DEPARTMENT" : "ROLE";
    }

    private String normalizeNodeType(String value) {
        String text = firstText(value, "process").toLowerCase(Locale.ROOT);
        if (text.contains("start")) return "start";
        if (text.contains("end")) return "end";
        if (text.contains("decision") || text.contains("judge")) return "decision";
        return "process";
    }

    private String slug(String value) {
        String text = firstText(value, "lane").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-+|-+$", "");
        return StringUtils.hasText(text) ? text : "lane";
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new BusinessException(400, "文件不能超过 20MB");
        }
        if (!List.of(".pdf", ".doc", ".docx", ".ppt", ".pptx", ".md", ".markdown")
                .contains(extensionOf(file.getOriginalFilename()))) {
            throw new BusinessException(400, "仅支持 pdf、doc、docx、ppt、pptx、md");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new BusinessException(500, "流程图数据序列化失败");
        }
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
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<FlowchartDTO.FileRef> objectToFileRefs(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<FlowchartDTO.FileRef> files = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> file = (Map<String, Object>) map;
            FlowchartDTO.FileRef ref = new FlowchartDTO.FileRef();
            ref.setId(getString(file, "id", ""));
            ref.setName(firstText(getString(file, "name", ""), getString(file, "fileName", "")));
            ref.setUrl(firstText(getString(file, "url", ""), getString(file, "sourceFile", "")));
            ref.setSize(getLong(file.get("size")));
            ref.setSummary(getString(file, "summary", ""));
            ref.setText(getString(file, "text", ""));
            ref.setTextLength(getInteger(file.get("textLength")));
            ref.setTruncated(getBooleanObject(file.get("truncated")));
            ref.setPageCount(getInteger(file.get("pageCount")));
            ref.setSlideCount(getInteger(file.get("slideCount")));
            ref.setParagraphCount(getInteger(file.get("paragraphCount")));
            files.add(ref);
        }
        return files;
    }

    private String firstFileSummary(List<FlowchartDTO.FileRef> files) {
        if (files == null || files.isEmpty()) return "";
        return trim(files.get(0).getSummary());
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private Long getLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Integer getInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean getBooleanObject(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text && StringUtils.hasText(text)) return Boolean.parseBoolean(text);
        return null;
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
