package com.example.appbackend.controller;

import com.example.appbackend.dto.StudyGoalDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.StudyGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    @PostMapping(value = "/decompose-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "粘贴文本解析", description = "接收学习计划文本，调用智能体返回 Goal+Tasks 结构化预览（不入库）")
    public Result<StudyGoalDTO.DecomposeResponse> decomposeText(
            @RequestBody StudyGoalDTO.TextDecomposeRequest request,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        String authorization = httpRequest.getHeader("Authorization");
        String planText = request == null ? null : request.getPlanText();
        return Result.success(studyGoalService.decompose(userId, null, planText, authorization));
    }

    @PostMapping("/save")
    @Operation(summary = "确认入库", description = "将预览的 Goal 与 Tasks 一并写入数据库并返回详情")
    public Result<StudyGoalDTO.GoalDetail> save(@RequestBody StudyGoalDTO.SaveRequest request,
                                                 HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return Result.success(studyGoalService.saveGoal(userId, request));
    }

    @PutMapping("/tasks/{taskId}/completion")
    @Operation(summary = "更新任务完成状态", description = "前端勾选 Checkbox 后调用，同步任务状态并自动重算 Goal 进度")
    public Result<StudyGoalDTO.GoalView> updateCompletion(@PathVariable("taskId") Long taskId,
                                                           @RequestBody StudyGoalDTO.TaskCompletionRequest request,
                                                           HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        boolean isCompleted = request != null && Boolean.TRUE.equals(request.getIsCompleted());
        return Result.success(studyGoalService.updateTaskCompletion(taskId, isCompleted, userId));
    }

    @GetMapping("/{goalId}")
    @Operation(summary = "查询目标详情", description = "filter 支持 all/pending（剩余）/completed（已完成）")
    public Result<StudyGoalDTO.GoalDetail> detail(@PathVariable("goalId") Long goalId,
                                                   @RequestParam(value = "filter", required = false) String filter,
                                                   HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return Result.success(studyGoalService.getGoalDetail(goalId, userId, filter));
    }

    @GetMapping("/{goalId}/remaining-tasks")
    @Operation(summary = "查询剩余任务", description = "返回指定 Goal 下 is_completed = false 的任务")
    public Result<List<StudyGoalDTO.TaskView>> remainingTasks(@PathVariable("goalId") Long goalId,
                                                               HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return Result.success(studyGoalService.getRemainingTasks(goalId, userId));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
