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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/app/ai/ppt")
public class AppAiPptController {
    private final AiPptService aiPptService;

    public AppAiPptController(AiPptService aiPptService) {
        this.aiPptService = aiPptService;
    }

    @GetMapping("/options")
    public Result<AiPptDTO.OptionsResponse> getOptions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return Result.success(aiPptService.getOptions(requireUserId(request), authorization));
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> uploadSourceFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return Result.success(aiPptService.uploadSourceFile(requireUserId(request), file, authorization));
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

    @PostMapping("/tasks/{taskId}/cancel")
    public Result<Object> cancelTask(@PathVariable String taskId,
                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                     HttpServletRequest request) {
        return Result.success(aiPptService.cancelTask(requireUserId(request), taskId, authorization));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public Result<Object> retryTask(@PathVariable String taskId,
                                    @RequestHeader(value = "Authorization", required = false) String authorization,
                                    HttpServletRequest request) {
        return Result.success(aiPptService.retryTask(requireUserId(request), taskId, authorization));
    }

    @PostMapping("/tasks/{taskId}/slides/{slideIndex}/image")
    public Result<Object> replaceSlideImage(@PathVariable String taskId,
                                            @PathVariable Integer slideIndex,
                                            @Valid @RequestBody AiPptDTO.SlideImageRequest body,
                                            @RequestHeader(value = "Authorization", required = false) String authorization,
                                            HttpServletRequest request) {
        return Result.success(aiPptService.replaceSlideImage(
                requireUserId(request), taskId, slideIndex, body, authorization));
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

    @GetMapping("/templates/{templateId}/thumbnail")
    public ResponseEntity<byte[]> downloadTemplateThumbnail(
            @PathVariable String templateId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        PythonAiProxyService.GeneratedExportResponse file = aiPptService.downloadTemplateThumbnail(
                requireUserId(request), templateId, authorization);
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.bytes().length)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
                .body(file.bytes());
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
