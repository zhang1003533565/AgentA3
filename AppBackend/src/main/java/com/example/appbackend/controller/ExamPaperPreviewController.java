package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewFile;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ExamPaperPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/exam/papers/preview")
public class ExamPaperPreviewController {
    private final ExamPaperPreviewService service;

    public ExamPaperPreviewController(ExamPaperPreviewService service) { this.service = service; }

    @PostMapping
    public Result<PreviewResponse> create(@Valid @RequestBody CreateRequest request, HttpServletRequest httpRequest) {
        return Result.success(service.createPreview(request, userId(httpRequest)));
    }

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> get(@PathVariable String token, HttpServletRequest request) {
        PreviewFile file = service.getPreview(token, userId(request));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @DeleteMapping("/{token}")
    public Result<Void> delete(@PathVariable String token, HttpServletRequest request) {
        service.deletePreview(token, userId(request));
        return Result.success();
    }

    private Long userId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) userId;
    }
}
