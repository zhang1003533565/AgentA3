package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
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

    @PutMapping("/tasks/{taskId}/progress")
    @Operation(summary = "更新任务进度", description = "支持 0-100 的部分完成进度，并按任务预计天数加权重算目标进度")
    public Result<StudyGoalDTO.GoalView> updateProgress(@PathVariable("taskId") Long taskId,
                                                         @RequestBody StudyGoalDTO.TaskProgressRequest request,
                                                         HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        Integer progress = request == null ? null : request.getProgressPercent();
        return Result.success(studyGoalService.updateTaskProgress(taskId, progress, userId));
    }

    @PutMapping("/tasks/{taskId}/status")
    @Operation(summary = "更新任务状态", description = "状态支持 pending/in_progress/blocked/skipped/completed")
    public Result<StudyGoalDTO.GoalView> updateStatus(@PathVariable("taskId") Long taskId,
                                                       @RequestBody StudyGoalDTO.TaskStatusRequest request,
                                                       HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        String status = request == null ? null : request.getStatus();
        return Result.success(studyGoalService.updateTaskStatus(taskId, status, userId));
    }

    @PutMapping("/subtasks/{subtaskId}/completion")
    @Operation(summary = "更新细分任务完成状态", description = "更新叶子任务并自动重算父任务与 Goal 进度")
    public Result<StudyGoalDTO.GoalView> updateSubtaskCompletion(
            @PathVariable("subtaskId") Long subtaskId,
            @RequestBody StudyGoalDTO.TaskCompletionRequest request,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        boolean isCompleted = request != null && Boolean.TRUE.equals(request.getIsCompleted());
        return Result.success(studyGoalService.updateSubtaskCompletion(subtaskId, isCompleted, userId));
    }

    @PutMapping("/subtasks/{subtaskId}/progress")
    @Operation(summary = "更新细分任务进度", description = "支持 0-100 的叶子任务进度，并自动聚合父任务与 Goal 进度")
    public Result<StudyGoalDTO.GoalView> updateSubtaskProgress(
            @PathVariable("subtaskId") Long subtaskId,
            @RequestBody StudyGoalDTO.TaskProgressRequest request,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        Integer progress = request == null ? null : request.getProgressPercent();
        return Result.success(studyGoalService.updateSubtaskProgress(subtaskId, progress, userId));
    }

    @PutMapping("/subtasks/{subtaskId}/status")
    @Operation(summary = "更新细分任务状态", description = "状态支持 pending/in_progress/blocked/skipped/completed")
    public Result<StudyGoalDTO.GoalView> updateSubtaskStatus(
            @PathVariable("subtaskId") Long subtaskId,
            @RequestBody StudyGoalDTO.TaskStatusRequest request,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        String status = request == null ? null : request.getStatus();
        return Result.success(studyGoalService.updateSubtaskStatus(subtaskId, status, userId));
    }

    @PostMapping("/tasks/{taskId}/postpone")
    @Operation(summary = "延后任务", description = "将指定任务及其之后的未完成任务整体顺延指定天数")
    public Result<StudyGoalDTO.GoalDetail> postpone(@PathVariable("taskId") Long taskId,
                                                     @RequestBody StudyGoalDTO.TaskPostponeRequest request,
                                                     HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        Integer days = request == null ? null : request.getDays();
        return Result.success(studyGoalService.postponeTask(taskId, days, userId));
    }

    @PostMapping("/subtasks/{subtaskId}/postpone")
    @Operation(summary = "延后细分任务", description = "将指定细分任务及其之后的未完成叶子任务整体顺延指定天数")
    public Result<StudyGoalDTO.GoalDetail> postponeSubtask(
            @PathVariable("subtaskId") Long subtaskId,
            @RequestBody StudyGoalDTO.TaskPostponeRequest request,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        Integer days = request == null ? null : request.getDays();
        return Result.success(studyGoalService.postponeSubtask(subtaskId, days, userId));
    }

    @GetMapping("/my")
    @Operation(summary = "我的学习计划", description = "分页返回当前用户创建过的计划（按更新时间倒序），退出后可随时找回继续勾选")
    public Result<PageResponse<StudyGoalDTO.GoalSummary>> myGoals(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return Result.success(studyGoalService.listMyGoals(userId, page, size));
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
