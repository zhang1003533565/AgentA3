package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ExamQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exam/banks")
@Tag(name = "题库分组", description = "按来源标题（sourceTitle）聚合的题库名称列表")
public class ExamBankController {

    private final ExamQuestionService examQuestionService;

    public ExamBankController(ExamQuestionService examQuestionService) {
        this.examQuestionService = examQuestionService;
    }

    @GetMapping
    @Operation(summary = "题库名称列表", description = "返回当前用户可见题目的去重题库名，用于筛选与录题下拉")
    public Result<List<String>> list(HttpServletRequest httpRequest) {
        return Result.success(examQuestionService.listBanks(getUserId(httpRequest)));
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }
}
