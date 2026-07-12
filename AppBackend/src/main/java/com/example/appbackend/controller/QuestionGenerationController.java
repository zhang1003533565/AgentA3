package com.example.appbackend.controller;

import com.example.appbackend.dto.QuestionGenerationDTO.GenerationResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.QuestionGenerationService;
import com.example.appbackend.service.QuestionGenerationService.GenerationCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/exam/question-generation")
public class QuestionGenerationController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> SOURCE_TYPES = Set.of("text", "txt", "docx", "file");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> QUESTION_TYPES = Set.of(
            "single_choice", "multiple_choice", "true_false", "fill_blank", "short_answer");

    private final QuestionGenerationService questionGenerationService;

    public QuestionGenerationController(QuestionGenerationService questionGenerationService) {
        this.questionGenerationService = questionGenerationService;
    }

    @GetMapping("/options")
    public Result<OptionsResponse> options(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(questionGenerationService.getOptions(request.getHeader("Authorization")));
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GenerationResponse> generate(
            @RequestParam String sourceType,
            @RequestPart(required = false) MultipartFile file,
            @RequestParam(required = false) String text,
            @RequestParam String questionType,
            @RequestParam(required = false) Integer maxQuestions,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String sourceTitle,
            HttpServletRequest request) {
        requireAdmin(request);
        String normalizedSourceType = normalizeSourceType(sourceType, file, text);
        String normalizedDifficulty = normalizeDifficulty(difficulty);
        String normalizedSourceTitle = normalizeSourceTitle(sourceTitle);
        validate(questionType, maxQuestions);
        GenerationCommand command = new GenerationCommand(
                normalizedSourceType, file, text, questionType, maxQuestions,
                normalizedDifficulty, normalizedSourceTitle);
        return Result.success(questionGenerationService.generate(
                command, request.getHeader("Authorization")));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!ROLE_ADMIN.equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        }
    }

    private void validate(String questionType, Integer maxQuestions) {
        if (!QUESTION_TYPES.contains(questionType)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题型不合法");
        }
        if (maxQuestions != null && maxQuestions < 1) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "最大题量必须大于 0");
        }
    }

    private String normalizeSourceType(String sourceType, MultipartFile file, String text) {
        String normalized = sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) {
            throw badRequest("来源类型不合法");
        }
        if ("text".equals(normalized)) {
            if (text == null || text.isBlank()) {
                throw badRequest("材料内容不能为空");
            }
            return normalized;
        }
        if (file == null || file.isEmpty()) {
            throw badRequest("材料文件不能为空");
        }
        String extension = fileExtension(file.getOriginalFilename());
        if (!"txt".equals(extension) && !"docx".equals(extension)) {
            throw badRequest("材料文件仅支持 .txt 或 .docx");
        }
        if (!"file".equals(normalized) && !normalized.equals(extension)) {
            throw badRequest("来源类型与文件扩展名不匹配");
        }
        return extension;
    }

    private String fileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        int separator = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        int dot = normalized.lastIndexOf('.');
        return dot > separator && dot < normalized.length() - 1 ? normalized.substring(dot + 1) : "";
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return null;
        }
        String normalized = difficulty.trim();
        if (!DIFFICULTIES.contains(normalized)) {
            throw badRequest("难度必须是 easy、medium 或 hard");
        }
        return normalized;
    }

    private String normalizeSourceTitle(String sourceTitle) {
        if (sourceTitle == null || sourceTitle.isBlank()) {
            return null;
        }
        String normalized = sourceTitle.trim();
        if (normalized.length() > 160) {
            throw badRequest("来源标题不能超过 160 个字符");
        }
        return normalized;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(Result.BAD_REQUEST_CODE, message);
    }
}
