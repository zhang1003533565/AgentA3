package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AiPptDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AiPptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class AiPptServiceImpl implements AiPptService {
    // Python tasks have a bounded 15-minute deadline; leave a small transport
    // margin so the stream closes after the task reaches a terminal state.
    private static final long SSE_TIMEOUT_MILLIS = 20 * 60 * 1000L;
    private static final long OPTIONS_CACHE_TTL_SECONDS = 24 * 60 * 60L;
    private final PythonAiProxyService pythonAiProxyService;
    private final ObjectMapper objectMapper;

    public AiPptServiceImpl(PythonAiProxyService pythonAiProxyService, ObjectMapper objectMapper) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiPptDTO.OptionsResponse getOptions(Long userId, String authorization) {
        requireUser(userId);
        try {
            AiPptDTO.OptionsResponse dynamic = objectMapper.convertValue(
                    pythonAiProxyService.getPptOptions(authorization),
                    AiPptDTO.OptionsResponse.class
            );
            if (dynamic.getTemplates() != null && !dynamic.getTemplates().isEmpty()) {
                dynamic.setTemplateCatalogAvailable(true);
                return dynamic;
            }
        } catch (RuntimeException ignored) {
            // Keep the entry page usable while the built-in Presenton template
            // catalog is temporarily unavailable; generation still requires it.
        }
        AiPptDTO.OptionsResponse response = new AiPptDTO.OptionsResponse();
        AiPptDTO.TemplateOption general = new AiPptDTO.TemplateOption();
        general.setId("general");
        general.setName("简约通用");
        general.setDescription("清晰留白，适合课程复习与知识讲解");
        general.setLayoutCount(12);
        general.setDefaultOption(true);
        response.setTemplates(List.of(general));
        response.setEngine("presenton-embedded");
        response.setEnhancedEngineAvailable(true);
        response.setEditorEnabled(false);
        response.setTemplateCatalogAvailable(false);
        response.setCacheTtlSeconds(OPTIONS_CACHE_TTL_SECONDS);
        return response;
    }

    @Override
    public Object uploadSourceFile(Long userId, MultipartFile file, String authorization) {
        requireUser(userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 资料文件不能为空");
        }
        if (file.getSize() > 25L * 1024L * 1024L) {
            throw new BusinessException(413, "PPT 资料文件不能超过 25MB");
        }
        String filename = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "material"
        );
        if (filename.contains("..") || !filename.matches("(?i).+\\.(txt|doc|docx|ppt|pptx|xls|xlsx)$")) {
            throw new BusinessException(Result.ERROR_CODE, "不支持的 PPT 资料文件格式");
        }
        return pythonAiProxyService.uploadPptSourceFile(file, authorization);
    }

    @Override
    public Object generateOutline(Long userId, AiPptDTO.OutlineRequest request, String authorization) {
        requireUser(userId);
        if (!StringUtils.hasText(request.getSourceContent()) && !StringUtils.hasText(request.getSourceFileId())) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 资料内容和资料文件不能同时为空");
        }
        return pythonAiProxyService.generatePptOutline(objectMapper.convertValue(request, Map.class), authorization);
    }

    @Override
    public Object createOutlineTask(Long userId, AiPptDTO.OutlineRequest request, String authorization) {
        requireUser(userId);
        if (!StringUtils.hasText(request.getSourceContent()) && !StringUtils.hasText(request.getSourceFileId())) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 资料内容和资料文件不能同时为空");
        }
        return pythonAiProxyService.createPptOutlineTask(
                objectMapper.convertValue(request, Map.class), authorization);
    }

    @Override
    public Object generateSlides(Long userId, AiPptDTO.SlidesRequest request, String authorization) {
        requireUser(userId);
        return pythonAiProxyService.generatePptSlides(objectMapper.convertValue(request, Map.class), authorization);
    }

    @Override
    public Object renderPreview(Long userId, AiPptDTO.PreviewRequest request, String authorization) {
        requireUser(userId);
        return pythonAiProxyService.renderPptPreview(
                objectMapper.convertValue(request, Map.class), authorization);
    }

    @Override
    public Object createSlidesTask(Long userId, AiPptDTO.SlidesRequest request, String authorization) {
        requireUser(userId);
        return pythonAiProxyService.createPptSlidesTask(
                objectMapper.convertValue(request, Map.class), authorization);
    }

    @Override
    public Object createTask(Long userId, AiPptDTO.TaskRequest request, String authorization) {
        requireUser(userId);
        return pythonAiProxyService.createPptTask(objectMapper.convertValue(request, Map.class), authorization);
    }

    @Override
    public Object getTask(Long userId, String taskId, String authorization) {
        requireTask(userId, taskId);
        return pythonAiProxyService.getPptTask(taskId.trim(), authorization);
    }

    @Override
    public Object cancelTask(Long userId, String taskId, String authorization) {
        requireTask(userId, taskId);
        return pythonAiProxyService.cancelPptTask(taskId.trim(), authorization);
    }

    @Override
    public Object retryTask(Long userId, String taskId, String authorization) {
        requireTask(userId, taskId);
        return pythonAiProxyService.retryPptTask(taskId.trim(), authorization);
    }

    @Override
    public Object replaceSlideImage(Long userId, String taskId, Integer slideIndex,
                                    AiPptDTO.SlideImageRequest request, String authorization) {
        requireTask(userId, taskId);
        if (slideIndex == null || slideIndex < 1 || slideIndex > 50) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 页面编号无效");
        }
        if (request == null || !StringUtils.hasText(request.getImageBase64())) {
            throw new BusinessException(Result.ERROR_CODE, "图片不能为空");
        }
        return pythonAiProxyService.replacePptSlideImage(
                taskId.trim(), slideIndex, objectMapper.convertValue(request, Map.class), authorization);
    }

    @Override
    public SseEmitter streamTask(Long userId, String taskId, String authorization) {
        requireTask(userId, taskId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            String previousMarker = "";
            long lastHeartbeatMillis = System.currentTimeMillis();
            try {
                while (true) {
                    Object value = getTask(userId, taskId, authorization);
                    Map<?, ?> task = value instanceof Map<?, ?> map ? map : Map.of("status", "unknown");
                    String status = String.valueOf(task.containsKey("status") ? task.get("status") : "unknown");
                    String stage = String.valueOf(task.containsKey("stage") ? task.get("stage") : "message");
                    String marker = status + ":" + task.get("progress") + ":" + stage
                            + ":" + task.get("currentSlide")
                            + ":" + task.get("completedSlides")
                            + ":" + task.get("remainingSlides")
                            + ":" + task.get("processingSlides");
                    if (!marker.equals(previousMarker)) {
                        emitter.send(SseEmitter.event().name(safeEventName(stage)).data(task, MediaType.APPLICATION_JSON));
                        previousMarker = marker;
                        lastHeartbeatMillis = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - lastHeartbeatMillis >= 15_000L) {
                        emitter.send(SseEmitter.event().comment("keepalive"));
                        lastHeartbeatMillis = System.currentTimeMillis();
                    }
                    if ("completed".equals(status) || "failed".equals(status)
                            || "cancelled".equals(status) || "timed_out".equals(status)) {
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(400L);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(error);
            } catch (Exception error) {
                emitter.completeWithError(error);
            }
        });
        return emitter;
    }

    @Override
    public PythonAiProxyService.GeneratedExportResponse downloadFile(
            Long userId, String taskId, String format, String authorization) {
        requireTask(userId, taskId);
        if (!"pptx".equals(format)) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 生成任务仅支持下载 PPTX");
        }
        return pythonAiProxyService.downloadPptTaskArtifact(
                taskId.trim() + "/files/" + format, authorization);
    }

    @Override
    public PythonAiProxyService.GeneratedExportResponse downloadPreview(
            Long userId, String taskId, Integer slideIndex, String authorization) {
        requireTask(userId, taskId);
        if (slideIndex == null || slideIndex < 1) {
            throw new BusinessException(Result.ERROR_CODE, "预览页码必须大于 0");
        }
        return pythonAiProxyService.downloadPptTaskArtifact(
                taskId.trim() + "/previews/" + slideIndex, authorization);
    }

    @Override
    public PythonAiProxyService.GeneratedExportResponse downloadTemplateThumbnail(
            Long userId, String templateId, String authorization) {
        requireUser(userId);
        if (!StringUtils.hasText(templateId) || !templateId.matches("[A-Za-z0-9._-]{1,120}")) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 模板编号无效");
        }
        return pythonAiProxyService.downloadPptTemplateThumbnail(templateId, authorization);
    }

    @Override
    public PythonAiProxyService.GeneratedExportResponse downloadTemplateLayoutPreview(
            Long userId, String templateId, Integer slideIndex, String authorization) {
        requireUser(userId);
        if (!StringUtils.hasText(templateId) || !templateId.matches("[A-Za-z0-9._-]{1,120}")) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 模板编号无效");
        }
        if (slideIndex == null || slideIndex < 1) {
            throw new BusinessException(Result.ERROR_CODE, "版式页码必须大于 0");
        }
        return pythonAiProxyService.downloadPptTemplateLayoutPreview(templateId, slideIndex, authorization);
    }

    private void requireTask(Long userId, String taskId) {
        requireUser(userId);
        if (!StringUtils.hasText(taskId) || !taskId.matches("ppt_task_[a-f0-9]{32}")) {
            throw new BusinessException(Result.ERROR_CODE, "PPT 任务编号无效");
        }
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
    }

    private String safeEventName(String stage) {
        return StringUtils.hasText(stage) && stage.matches("[A-Za-z][A-Za-z0-9_-]{0,39}")
                ? stage : "message";
    }
}
