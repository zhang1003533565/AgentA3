package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MindMapDTO;
import com.example.appbackend.entity.MindMapRecord;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MindMapRecordRepository;
import com.example.appbackend.service.FileParseService;
import com.example.appbackend.service.MindMapAIService;
import com.example.appbackend.service.MindMapService;
import com.example.appbackend.service.support.MindMapTopicExtractor;
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
public class MindMapServiceImpl implements MindMapService {
    private static final int PREVIEW_LENGTH = 80;

    private final MindMapAIService mindMapAIService;
    private final FileParseService fileParseService;
    private final MindMapRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String fileBaseUrl;

    public MindMapServiceImpl(MindMapAIService mindMapAIService,
                              FileParseService fileParseService,
                              MindMapRecordRepository recordRepository,
                              ObjectMapper objectMapper) {
        this.mindMapAIService = mindMapAIService;
        this.fileParseService = fileParseService;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public MindMapDTO.GenerateResponse generate(Long userId, MindMapDTO.GenerateRequest request, String authorization) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        String topic = trim(request.getTopic());
        String centerTopic = trim(request.getCenterTopic());
        String sourceText = trim(request.getSourceText());
        String resolvedCenterTopic = MindMapTopicExtractor.extract(centerTopic, topic, sourceText, request.getSourceFile());
        String inputText = composeInputText(topic, sourceText, resolvedCenterTopic);
        if (!StringUtils.hasText(inputText)) {
            throw new BusinessException(400, "请输入主题或上传可解析文件");
        }

        MindMapDTO.MindMapData data = mindMapAIService.generate(
                inputText,
                resolvedCenterTopic,
                request.getDepth(),
                request.getStructure(),
                request.getDetail(),
                authorization
        );

        MindMapRecord record = new MindMapRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setTitle(data.getTitle());
        record.setSourceType(StringUtils.hasText(sourceText) ? MindMapRecord.SOURCE_FILE : MindMapRecord.SOURCE_TEXT);
        record.setSourceFile(firstText(request.getSourceFile(), request.getFileId()));
        record.setContent(inputText);
        record.setMindMapJson(writeJson(data));
        record.setDepth(request.getDepth());
        record.setStructureType(request.getStructure());
        record.setDetailLevel(request.getDetail());
        recordRepository.save(record);
        return toGenerateResponse(record, data);
    }

    private String composeInputText(String topic, String sourceText, String centerTopic) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(centerTopic)) {
            builder.append("建议中心主题：").append(centerTopic).append("\n");
        }
        if (StringUtils.hasText(topic)) {
            builder.append("用户输入要求：").append(topic).append("\n");
        }
        if (StringUtils.hasText(sourceText)) {
            builder.append("文件解析内容：\n").append(sourceText);
        }
        return builder.toString().trim();
    }

    @Override
    public MindMapDTO.GenerateResponse optimize(Long userId, MindMapDTO.OptimizeRequest request, String authorization) {
        if (request == null || request.getCurrentMindMap() == null) {
            throw new BusinessException(400, "请提供当前思维导图数据");
        }
        String instruction = trim(request.getUserInstruction());
        if (!StringUtils.hasText(instruction)) {
            throw new BusinessException(400, "请输入优化要求");
        }

        MindMapDTO.MindMapData data = mindMapAIService.optimize(request.getCurrentMindMap(), instruction, authorization);

        MindMapRecord record = new MindMapRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setTitle(data.getTitle());
        record.setSourceType(MindMapRecord.SOURCE_TEXT);
        record.setContent("优化：" + instruction);
        record.setMindMapJson(writeJson(data));
        recordRepository.save(record);
        return toGenerateResponse(record, data);
    }

    @Override
    public MindMapDTO.UploadResponse uploadAndParse(Long userId, MultipartFile file) {
        validateUpload(file);
        String originalName = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "mindmap-file");
        String extension = extensionOf(originalName);
        String fileId = UUID.randomUUID().toString();
        String objectKey = "mindmap/" + LocalDate.now() + "/" + fileId + extension;
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

        String text = fileParseService.parse(target.toFile());
        MindMapDTO.UploadResponse response = new MindMapDTO.UploadResponse();
        response.setFileId(fileId);
        response.setFileName(originalName);
        response.setSourceFile(buildFileUrl(objectKey));
        response.setText(text);
        return response;
    }

    @Override
    public List<MindMapDTO.HistoryItem> history(Long userId) {
        return recordRepository.findTop20ByUserIdOrderByCreateTimeDesc(userId)
                .stream()
                .map(this::toHistoryItem)
                .toList();
    }

    @Override
    public MindMapDTO.GenerateResponse detail(Long userId, String id) {
        MindMapRecord record = recordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(404, "思维导图记录不存在"));
        return toGenerateResponse(record, readData(record.getMindMapJson()));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!List.of(".pdf", ".doc", ".docx", ".ppt", ".pptx").contains(extension)) {
            throw new BusinessException(400, "仅支持 pdf、doc、docx、ppt、pptx");
        }
    }

    private MindMapDTO.GenerateResponse toGenerateResponse(MindMapRecord record, MindMapDTO.MindMapData data) {
        MindMapDTO.GenerateResponse response = new MindMapDTO.GenerateResponse();
        response.setId(record.getId());
        response.setTitle(data.getTitle());
        response.setNodes(data.getNodes());
        response.setCreateTime(record.getCreateTime());
        return response;
    }

    private MindMapDTO.HistoryItem toHistoryItem(MindMapRecord record) {
        MindMapDTO.HistoryItem item = new MindMapDTO.HistoryItem();
        item.setId(record.getId());
        item.setTitle(record.getTitle());
        item.setCreateTime(record.getCreateTime());
        item.setPreview(preview(record.getContent()));
        return item;
    }

    private String preview(String value) {
        String text = trim(value).replaceAll("\\s+", " ");
        return text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "..." : text;
    }

    private String writeJson(MindMapDTO.MindMapData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception error) {
            throw new BusinessException(500, "思维导图数据序列化失败");
        }
    }

    private MindMapDTO.MindMapData readData(String json) {
        try {
            return objectMapper.readValue(json, MindMapDTO.MindMapData.class);
        } catch (Exception error) {
            throw new BusinessException(500, "思维导图记录数据损坏");
        }
    }

    private String buildFileUrl(String objectKey) {
        String normalizedBaseUrl = StringUtils.hasText(fileBaseUrl)
                ? fileBaseUrl.trim().replaceAll("/+$", "")
                : "";
        return normalizedBaseUrl + "/uploads/" + objectKey.replace('\\', '/');
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : trim(second);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
