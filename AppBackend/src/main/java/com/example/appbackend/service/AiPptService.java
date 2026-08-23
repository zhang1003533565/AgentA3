package com.example.appbackend.service;

import com.example.appbackend.dto.AiPptDTO;
import com.example.appbackend.service.impl.PythonAiProxyService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

public interface AiPptService {
    AiPptDTO.OptionsResponse getOptions(Long userId, String authorization);

    Object uploadSourceFile(Long userId, MultipartFile file, String authorization);

    Object generateOutline(Long userId, AiPptDTO.OutlineRequest request, String authorization);

    default Object createOutlineTask(Long userId, AiPptDTO.OutlineRequest request, String authorization) {
        return generateOutline(userId, request, authorization);
    }

    Object generateSlides(Long userId, AiPptDTO.SlidesRequest request, String authorization);

    Object renderPreview(Long userId, AiPptDTO.PreviewRequest request, String authorization);

    default Object createSlidesTask(Long userId, AiPptDTO.SlidesRequest request, String authorization) {
        return generateSlides(userId, request, authorization);
    }

    Object createTask(Long userId, AiPptDTO.TaskRequest request, String authorization);

    Object getTask(Long userId, String taskId, String authorization);

    Object cancelTask(Long userId, String taskId, String authorization);

    Object retryTask(Long userId, String taskId, String authorization);

    Object replaceSlideImage(Long userId, String taskId, Integer slideIndex,
                             AiPptDTO.SlideImageRequest request, String authorization);

    SseEmitter streamTask(Long userId, String taskId, String authorization);

    PythonAiProxyService.GeneratedExportResponse downloadFile(
            Long userId, String taskId, String format, String authorization);

    PythonAiProxyService.GeneratedExportResponse downloadPreview(
            Long userId, String taskId, Integer slideIndex, String authorization);

    PythonAiProxyService.GeneratedExportResponse downloadTemplateThumbnail(
            Long userId, String templateId, String authorization);

    PythonAiProxyService.GeneratedExportResponse downloadTemplateLayoutPreview(
            Long userId, String templateId, Integer slideIndex, String authorization);
}
