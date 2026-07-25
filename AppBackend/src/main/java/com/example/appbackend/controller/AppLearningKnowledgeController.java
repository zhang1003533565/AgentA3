package com.example.appbackend.controller;

import com.example.appbackend.dto.LearningKnowledgeDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CourseKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/learning/knowledge")
@Tag(name = "App 课程知识", description = "学生端课程绑定知识检索")
public class AppLearningKnowledgeController {

    private final CourseKnowledgeService courseKnowledgeService;

    public AppLearningKnowledgeController(CourseKnowledgeService courseKnowledgeService) {
        this.courseKnowledgeService = courseKnowledgeService;
    }

    @PostMapping("/retrieve")
    @Operation(summary = "检索课程知识", description = "按服务端课程绑定检索净化后的知识引用")
    public Result<LearningKnowledgeDTO.RetrieveResponse> retrieve(
            @Valid @RequestBody LearningKnowledgeDTO.RetrieveRequest body,
            HttpServletRequest request
    ) {
        requireAuthenticatedUser(request);
        return Result.success(courseKnowledgeService.retrieve(body));
    }

    private void requireAuthenticatedUser(HttpServletRequest request) {
        if (!(request.getAttribute("userId") instanceof Number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
    }
}
