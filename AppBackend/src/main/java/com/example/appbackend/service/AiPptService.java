package com.example.appbackend.service;

import com.example.appbackend.dto.AiPptDTO;
import com.example.appbackend.service.impl.PythonAiProxyService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiPptService {
    AiPptDTO.OptionsResponse getOptions(Long userId);

    Object generateOutline(Long userId, AiPptDTO.OutlineRequest request, String authorization);

    Object generateSlides(Long userId, AiPptDTO.SlidesRequest request, String authorization);

    Object createTask(Long userId, AiPptDTO.TaskRequest request, String authorization);

    Object getTask(Long userId, String taskId, String authorization);

    SseEmitter streamTask(Long userId, String taskId, String authorization);

    PythonAiProxyService.GeneratedExportResponse downloadFile(
            Long userId, String taskId, String format, String authorization);

    PythonAiProxyService.GeneratedExportResponse downloadPreview(
            Long userId, String taskId, Integer slideIndex, String authorization);
}
