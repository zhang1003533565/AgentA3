package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ExamQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exam/questions")
@Tag(name = "题库管理", description = "AI 题库 JSON 校验、导入和查询")
public class ExamQuestionController {

    private final ExamQuestionService examQuestionService;

    public ExamQuestionController(ExamQuestionService examQuestionService) {
        this.examQuestionService = examQuestionService;
    }

    @PostMapping("/review")
    @Operation(summary = "审查题库 JSON", description = "返回问题清单和警告，不落库")
    public Result<ExamQuestionDTO.ReviewResponse> review(
            @Valid @RequestBody ExamQuestionDTO.ImportRequest request,
            @RequestParam(required = false) String expectedType,
            HttpServletRequest httpRequest) {
        requireLogin(httpRequest);
        return Result.success(examQuestionService.review(request, expectedType));
    }

    @PostMapping("/validate")
    @Operation(summary = "校验题库 JSON", description = "不符合规范会直接返回错误，不落库")
    public Result<ExamQuestionDTO.ReviewResponse> validate(
            @Valid @RequestBody ExamQuestionDTO.ImportRequest request,
            @RequestParam(required = false) String expectedType,
            HttpServletRequest httpRequest) {
        requireLogin(httpRequest);
        ExamQuestionDTO.ReviewResponse review = examQuestionService.review(request, expectedType);
        if (!Boolean.TRUE.equals(review.getValid())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题库 JSON 未通过校验：" + String.join("；", review.getIssues()));
        }
        return Result.success(review);
    }

    @PostMapping("/import")
    @Operation(summary = "导入题库 JSON", description = "先校验，校验通过后写入 exam_question 表")
    public Result<ExamQuestionDTO.ImportResponse> importQuestions(
            @Valid @RequestBody ExamQuestionDTO.ImportRequest request,
            @RequestParam(required = false) String expectedType,
            HttpServletRequest httpRequest) {
        if ("question_generation".equals(request.getSourceScene())) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "智能生成题目必须使用管理员专用导入接口");
        }
        return Result.success("导入成功", examQuestionService.importQuestions(request, expectedType, getUserId(httpRequest)));
    }

    @GetMapping
    @Operation(summary = "题库列表")
    public Result<PageResponse<ExamQuestionDTO.QuestionVO>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            HttpServletRequest httpRequest) {
        requireLogin(httpRequest);
        return Result.success(examQuestionService.listQuestions(current, size, type, difficulty, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "题目详情")
    public Result<ExamQuestionDTO.QuestionVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        requireLogin(httpRequest);
        return Result.success(examQuestionService.getQuestion(id));
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }

    private void requireLogin(HttpServletRequest request) {
        getUserId(request);
    }
}
