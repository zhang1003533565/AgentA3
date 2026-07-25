package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.RandomPreviewRequest;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ExamPaperService;
import com.example.appbackend.service.ExamPaperService.DownloadFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/exam/papers")
public class ExamPaperController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final String ILLEGAL_FILENAME_CHARACTERS = "[\\\\/:*?\"<>|\\r\\n]";

    private final ExamPaperService examPaperService;

    public ExamPaperController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    @PostMapping("/random-preview")
    public Result<PaperVO> randomPreview(@Valid @RequestBody RandomPreviewRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.success(examPaperService.randomPreview(request, getUserId(httpRequest)));
    }

    @PostMapping
    public Result<PaperVO> create(@Valid @RequestBody CreateRequest request,
                                  HttpServletRequest httpRequest) {
        return Result.success("创建成功", examPaperService.create(request, getUserId(httpRequest)));
    }

    @GetMapping
    public Result<PageResponse<PaperVO>> list(@RequestParam(defaultValue = "1") Integer current,
                                               @RequestParam(defaultValue = "10") Integer size,
                                               @RequestParam(required = false) String keyword,
                                               HttpServletRequest httpRequest) {
        return Result.success(examPaperService.list(current, size, keyword, getUserId(httpRequest)));
    }

    @GetMapping("/{id}")
    public Result<PaperVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.success(examPaperService.detail(id, getUserId(httpRequest)));
    }

    @PostMapping("/{id}/publish")
    public Result<PaperVO> publish(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.success("发布成功", examPaperService.publish(id, getAdminUserId(httpRequest)));
    }

    @PostMapping("/{id}/unpublish")
    public Result<PaperVO> unpublish(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.success("取消发布成功", examPaperService.unpublish(id, getAdminUserId(httpRequest)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id,
                                           @RequestParam(required = false) String content,
                                           HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        DownloadContent downloadContent = parseContent(content);
        DownloadFile file = examPaperService.download(id, userId, downloadContent);
        String filename = sanitizeFilename(file.title()) + "-" + id
                + (downloadContent == DownloadContent.ANSWER ? "-答案.docx" : "-试卷.docx");
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.bytes());
    }

    private DownloadContent parseContent(String content) {
        if (content == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "content 仅支持 paper 或 answer");
        }
        try {
            return DownloadContent.valueOf(content.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "content 仅支持 paper 或 answer");
        }
    }

    private String sanitizeFilename(String title) {
        String sanitized = title == null ? "" : title.replaceAll(ILLEGAL_FILENAME_CHARACTERS, "_").trim();
        return sanitized.isEmpty() ? "试卷" : sanitized;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }

    private Long getAdminUserId(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!ROLE_ADMIN.equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "仅管理员可发布试卷");
        }
        return userId;
    }
}
