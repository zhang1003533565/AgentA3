package com.example.appbackend.controller;

import com.example.appbackend.dto.StudyGoalDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.StudyGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学习计划结构化拆解接口。
 *
 * 链路：AI 创作界面入口 -> 独立工作流页面 -> 本控制器 -> ai-servers 专用智能体。
 * 鉴权依赖 JWT 拦截器写入 request 的 userId 属性。
 */
@RestController
@RequestMapping("/api/study-goal")
@Tag(name = "学习计划结构化拆解", description = "上传学习计划数据表/文本，AI 拆解为目标与可勾选任务")
public class StudyGoalController {

    private final StudyGoalService studyGoalService;

    public StudyGoalController(StudyGoalService studyGoalService) {
        this.studyGoalService = studyGoalService;
    }

    @PostMapping(value = "/decompose", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传并解析", description = "接收 .xlsx/.csv 文件或文本，调用智能体返回 Goal+Tasks 结构化预览（不入库）")
    public Result<StudyGoalDTO.DecomposeResponse> decompose(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "planText", required = false) String planText,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        String authorization = httpRequest.getHeader("Authorization");
        return Result.success(studyGoalService.decompose(userId, file, planText, authorization));
    }

    @PostMapping("/save")
    @Operation(summary = "确认入库", description = "将预览的 Goal 与 Tasks 一并写入数据库并返回详情")
    public Result<StudyGoalDTO.GoalDetail> save(@RequestBody StudyGoalDTO.SaveRequest request,
                                                 HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return Result.success(studyGoalService.saveGoal(userId, request));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
