package com.example.appbackend.controller;

import com.example.appbackend.dto.AiPptDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AiPptService;
import com.example.appbackend.service.impl.PythonAiProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/app/ai/ppt")
public class AppAiPptController {
    private final AiPptService aiPptService;

    public AppAiPptController(AiPptService aiPptService) {
        this.aiPptService = aiPptService;
    }

    @PostMapping("/outlines")
    public Result<Object> generateOutline(@Valid @RequestBody AiPptDTO.OutlineRequest body,
                                         @RequestHeader(value = "Authorization", required = false) String authorization,
                                         HttpServletRequest request) {
        return Result.success(aiPptService.generateOutline(requireUserId(request), body, authorization));
    }

    @PostMapping("/slides")
    public Result<Object> generateSlides(@Valid @RequestBody AiPptDTO.SlidesRequest body,
                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                        HttpServletRequest request) {
        return Result.success(aiPptService.generateSlides(requireUserId(request), body, authorization));
    }

    @PostMapping("/tasks")
    public Result<Object> createTask(@Valid @RequestBody AiPptDTO.TaskRequest body,
                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                     HttpServletRequest request) {
        return Result.success(aiPptService.createTask(requireUserId(request), body, authorization));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<Object> getTask(@PathVariable String taskId,
                                  @RequestHeader(value = "Authorization", required = false) String authorization,
                                  HttpServletRequest request) {
        return Result.success(aiPptService.getTask(requireUserId(request), taskId, authorization));
    }

    @GetMapping(value = "/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTask(@PathVariable String taskId,
                                 @RequestHeader(value = "Authorization", required = false) String authorization,
                                 HttpServletRequest request) {
        return aiPptService.streamTask(requireUserId(request), taskId, authorization);
    }

    @GetMapping("/tasks/{taskId}/files/{format}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String taskId, @PathVariable String format,
                                                @RequestHeader(value = "Authorization", required = false) String authorization,
                                                HttpServletRequest request) {
        return fileResponse(aiPptService.downloadFile(requireUserId(request), taskId, format, authorization),
                taskId + "." + format);
    }

    @GetMapping("/tasks/{taskId}/previews/{slideIndex}")
    public ResponseEntity<byte[]> downloadPreview(@PathVariable String taskId, @PathVariable Integer slideIndex,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization,
                                                   HttpServletRequest request) {
        return fileResponse(aiPptService.downloadPreview(requireUserId(request), taskId, slideIndex, authorization),
                taskId + "-slide-" + slideIndex + ".png");
    }

    private ResponseEntity<byte[]> fileResponse(PythonAiProxyService.GeneratedExportResponse file, String filename) {
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    private Long requireUserId(HttpServletRequest request) {
        Object raw = request.getAttribute("userId");
        if (!(raw instanceof Number number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return number.longValue();
    }
}
