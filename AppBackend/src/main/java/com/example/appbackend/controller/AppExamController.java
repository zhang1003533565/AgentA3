package com.example.appbackend.controller;

import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AppExamService;
import com.example.appbackend.service.exampaper.AppExamPdfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/app")
public class AppExamController {
    private static final String ILLEGAL_FILENAME_CHARACTERS = "[\\\\/:*?\"<>|\\r\\n]";
    private final AppExamService examService;
    private final AppExamPdfService pdfService;

    public AppExamController(AppExamService examService, AppExamPdfService pdfService) {
        this.examService = examService;
        this.pdfService = pdfService;
    }

    @GetMapping("/exam-papers")
    public Result<?> papers(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) String keyword,
                            HttpServletRequest request) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(400, "分页参数不合法");
        return Result.success(examService.listPublished(userId(request), page, size, keyword));
    }

    @GetMapping("/exam-papers/{paperId}")
    public Result<AppExamDTO.PaperDetail> paper(@PathVariable Long paperId, HttpServletRequest request) {
        return Result.success(examService.paperDetail(paperId, userId(request)));
    }

    @PostMapping("/exam-papers/{paperId}/attempts")
    public Result<AppExamDTO.AttemptDetail> start(@PathVariable Long paperId, HttpServletRequest request) {
        return Result.success(examService.startOrResume(paperId, userId(request), LocalDateTime.now()));
    }

    @GetMapping("/exam-papers/{paperId}/attempts")
    public Result<?> history(@PathVariable Long paperId, HttpServletRequest request) {
        return Result.success(examService.history(paperId, userId(request)));
    }

    @GetMapping("/exam-attempts/{attemptId}")
    public Result<AppExamDTO.AttemptDetail> attempt(@PathVariable Long attemptId, HttpServletRequest request) {
        return Result.success(examService.attemptDetail(attemptId, userId(request), LocalDateTime.now()));
    }

    @PutMapping("/exam-attempts/{attemptId}/answers/{paperQuestionId}")
    public Result<AppExamDTO.SavedAnswer> saveAnswer(@PathVariable Long attemptId,
            @PathVariable Long paperQuestionId, @Valid @RequestBody AppExamDTO.SaveAnswerRequest body,
            HttpServletRequest request) {
        return Result.success(examService.saveAnswer(
                attemptId, paperQuestionId, userId(request), body, LocalDateTime.now()));
    }

    @PostMapping("/exam-attempts/{attemptId}/submit")
    public Result<AppExamDTO.AttemptResult> submit(@PathVariable Long attemptId, HttpServletRequest request) {
        return Result.success(examService.submit(attemptId, userId(request), LocalDateTime.now()));
    }

    @GetMapping("/exam-attempts/{attemptId}/result")
    public Result<AppExamDTO.AttemptResult> result(@PathVariable Long attemptId, HttpServletRequest request) {
        return Result.success(examService.result(attemptId, userId(request)));
    }

    @GetMapping("/exam-papers/{paperId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long paperId, HttpServletRequest request) {
        AppExamPdfService.PdfFile file = pdfService.downloadBlankPaper(paperId, userId(request));
        String title = file.title() == null ? "试卷" : file.title().replaceAll(ILLEGAL_FILENAME_CHARACTERS, "_").trim();
        if (title.isEmpty()) title = "试卷";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(title + "-" + paperId + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    private Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) throw new BusinessException(401, "请先登录");
        return id;
    }
}
