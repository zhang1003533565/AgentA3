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

import java.util.Set;

@RestController
@RequestMapping("/api/exam/question-generation")
public class QuestionGenerationController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> SOURCE_TYPES = Set.of("text", "file");
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
        validate(sourceType, questionType, maxQuestions);
        GenerationCommand command = new GenerationCommand(
                sourceType, file, text, questionType, maxQuestions, difficulty, sourceTitle);
        return Result.success(questionGenerationService.generate(
                command, request.getHeader("Authorization")));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!ROLE_ADMIN.equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        }
    }

    private void validate(String sourceType, String questionType, Integer maxQuestions) {
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "来源类型不合法");
        }
        if (!QUESTION_TYPES.contains(questionType)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题型不合法");
        }
        if (maxQuestions != null && maxQuestions < 1) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "最大题量必须大于 0");
        }
    }
}
