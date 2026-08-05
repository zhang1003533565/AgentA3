package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FlowchartDTO;
import com.example.appbackend.entity.FlowchartRecord;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FlowchartRecordRepository;
import com.example.appbackend.service.FileParseService;
import com.example.appbackend.service.FlowchartAIService;
import com.example.appbackend.service.FlowchartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FlowchartServiceImpl implements FlowchartService {
    private final FlowchartAIService flowchartAIService;
    private final FileParseService fileParseService;
    private final FlowchartRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String fileBaseUrl;

    public FlowchartServiceImpl(FlowchartAIService flowchartAIService,
                                FileParseService fileParseService,
                                FlowchartRecordRepository recordRepository,
                                ObjectMapper objectMapper) {
        this.flowchartAIService = flowchartAIService;
        this.fileParseService = fileParseService;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public FlowchartDTO.GenerateResponse generate(Long userId, FlowchartDTO.GenerateRequest request, String authorization) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        String description = trim(request.getDescription());
        String sourceText = trim(request.getSourceText());
        String inputText = StringUtils.hasText(sourceText) ? sourceText : description;
        if (!StringUtils.hasText(inputText)) {
            throw new BusinessException(400, "请输入流程描述或上传可解析文件");
        }

        FlowchartDTO.FlowchartData data = flowchartAIService.generate(request, inputText, authorization);
        FlowchartRecord record = new FlowchartRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setTitle(data.getTitle());
        record.setDescription(inputText);
        record.setProcessType(request.getProcessType());
        record.setDiagramType(request.getDiagramType());
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
        response.setFileId(fileId);
        response.setFileName(originalName);
        response.setSourceFile(buildFileUrl(objectKey));
        response.setText(fileParseService.parse(target.toFile()));
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
            return toResponse(record, objectMapper.readValue(record.getFlowJson(), FlowchartDTO.FlowchartData.class));
        } catch (Exception error) {
            throw new BusinessException(500, "流程图记录数据损坏");
        }
    }

    private FlowchartDTO.GenerateResponse toResponse(FlowchartRecord record, FlowchartDTO.FlowchartData data) {
        FlowchartDTO.GenerateResponse response = new FlowchartDTO.GenerateResponse();
        response.setId(record.getId());
        response.setTitle(data.getTitle());
        response.setType(data.getType());
        response.setLanes(data.getLanes());
        response.setNodes(data.getNodes());
        response.setEdges(data.getEdges());
        response.setCreateTime(record.getCreateTime());
        return response;
    }

    private FlowchartDTO.HistoryItem toHistoryItem(FlowchartRecord record) {
        FlowchartDTO.HistoryItem item = new FlowchartDTO.HistoryItem();
        item.setId(record.getId());
        item.setTitle(record.getTitle());
        item.setCreateTime(record.getCreateTime());
        item.setType(record.getDiagramType());
        return item;
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new BusinessException(500, "流程图数据序列化失败");
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
