package com.example.appbackend.controller;

import com.example.appbackend.dto.AiWriteDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AiService;
import com.example.appbackend.service.SmartWritingDocumentGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 写作", description = "AI 写作接口")
public class AiController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final AiService aiService;
    private final SmartWritingDocumentGenerator documentGenerator;

    public AiController(AiService aiService, SmartWritingDocumentGenerator documentGenerator) {
        this.aiService = aiService;
        this.documentGenerator = documentGenerator;
    }

    @PostMapping("/write")
    @Operation(summary = "智能写作")
    public Result<AiWriteDTO.WriteResponse> write(
            @Valid @RequestBody AiWriteDTO.WriteRequest request,
            HttpServletRequest httpRequest) {
        Object userId = httpRequest.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return Result.success(aiService.write(request));
    }

    @GetMapping("/write/models")
    @Operation(summary = "智能写作可用模型")
    public Result<List<AiWriteDTO.ModelOption>> writingModels() {
        return Result.success(aiService.listAvailableTextModels());
    }

    @PostMapping("/write/export")
    @Operation(summary = "智能写作导出为 Word 文档")
    public ResponseEntity<byte[]> exportWord(
            @Valid @RequestBody AiWriteDTO.ExportRequest request,
            HttpServletRequest httpRequest) {
        Object userId = httpRequest.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");

        byte[] bytes = documentGenerator.generate(request);
        String filename = sanitizeFilename(request.getTitle()) + ".docx";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    private String sanitizeFilename(String title) {
        String sanitized = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return sanitized.isEmpty() ? "智能写作" : sanitized;
    }
}
