package com.example.appbackend.controller;

import com.example.appbackend.dto.AgentConfirmTaskRequest;
import com.example.appbackend.dto.CreateTaskRequest;
import com.example.appbackend.dto.TaskDetailVO;
import com.example.appbackend.dto.UpdateTaskStatusRequest;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.MeetingTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会议个人任务控制器
 */
@RestController
@RequestMapping("/api/meeting-tasks")
@Tag(name = "MeetingTasks", description = "会议个人任务管理 API")
public class MeetingTaskController {

    private final MeetingTaskService taskService;

    public MeetingTaskController(MeetingTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 创建个人任务（由 Agent 2 或前端调用）
     */
    @PostMapping
    @Operation(summary = "创建个人任务", description = "Agent 会后分析时调用此接口创建任务")
    public Result<TaskDetailVO> createTask(
            @RequestBody CreateTaskRequest request,
            HttpServletRequest httpRequest) {
        TaskDetailVO vo = taskService.createTask(request);
        return Result.success(vo);
    }

    /**
     * 查询当前登录用户的个人任务列表
     */
    @GetMapping("/my")
    @Operation(summary = "查询我的任务", description = "查看当前用户自己的任务列表")
    public Result<List<TaskDetailVO>> listMyTasks(
            @Parameter(description = "会议 ID（可选）") @RequestParam(required = false) Long meetingSessionId,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) String status,
            HttpServletRequest httpRequest) {
        
        // 获取当前登录用户 ID（从 JWT token 中解析）
        Long currentUserId = getCurrentUserId(httpRequest);
        
        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }

        List<TaskDetailVO> tasks = taskService.listMyTasks(currentUserId, meetingSessionId, status);
        return Result.success(tasks);
    }

    /**
     * 根据 ID 查询任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务详情", description = "查看单个任务的详细信息")
    public Result<TaskDetailVO> getTaskById(
            @PathVariable Long taskId,
            HttpServletRequest httpRequest) {
        
        // 获取当前登录用户 ID
        Long currentUserId = getCurrentUserId(httpRequest);
        
        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }

        TaskDetailVO vo = taskService.getTaskById(taskId, currentUserId);
        return Result.success(vo);
    }

    /**
     * 更新任务状态（只有任务负责人本人可以完成任务）
     */
    @PatchMapping("/{taskId}/status")
    @Operation(summary = "更新任务状态", description = "只有任务负责人本人才能更新该任务状态为已完成")
    public Result<TaskDetailVO> updateTaskStatus(
            @PathVariable Long taskId,
            @RequestBody UpdateTaskStatusRequest request,
            HttpServletRequest httpRequest) {
        
        // 获取当前登录用户 ID
        Long currentUserId = getCurrentUserId(httpRequest);
        
        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }

        TaskDetailVO vo = taskService.updateTaskStatus(taskId, currentUserId, request);
        return Result.success(vo);
    }

    /**
     * AI 确认任务完成（第五步）：会议 AI 识别到任务负责人本人在会议中明确确认完成后调用。
     * 服务端强校验 assigneeId 必须等于任务真实负责人，且该负责人是本会议真实参会人；
     * completedBy 记录任务负责人本人。
     */
    @PostMapping("/{taskId}/agent-confirm")
    @Operation(summary = "AI 确认任务完成",
            description = "会议 AI 识别到任务负责人本人明确确认完成后调用；服务端校验负责人身份，不信任 AI 传入的其他身份")
    public Result<TaskDetailVO> agentConfirmTaskCompletion(
            @PathVariable Long taskId,
            @RequestBody AgentConfirmTaskRequest request,
            HttpServletRequest httpRequest) {

        Long currentUserId = getCurrentUserId(httpRequest);

        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }

        TaskDetailVO vo = taskService.agentConfirmTaskCompletion(taskId, request);
        return Result.success(vo);
    }

    /**
     * 获取当前登录用户 ID（从请求属性中解析）
     */
    private Long getCurrentUserId(HttpServletRequest httpRequest) {
        Object userId = httpRequest.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }
}
