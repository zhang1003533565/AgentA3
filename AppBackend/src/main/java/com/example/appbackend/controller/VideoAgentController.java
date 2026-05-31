package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.impl.PythonAiProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/videos")
@Tag(name = "视频智能体", description = "代理 Python FastAPI 的 Qwen 视频生成智能体接口")
public class VideoAgentController {

    private final PythonAiProxyService pythonAiProxyService;

    public VideoAgentController(PythonAiProxyService pythonAiProxyService) {
        this.pythonAiProxyService = pythonAiProxyService;
    }

    @PostMapping("/generate")
    @Operation(summary = "单个生成视频", description = "调用视频智能体生成单个文生视频或图生视频，返回任务状态和视频 URL")
    public Result<Object> generateVideo(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.generateVideo(body, authorization(request)));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量生成视频", description = "调用视频智能体批量生成视频，返回每个任务的状态和结果")
    public Result<Object> generateVideosBatch(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.generateVideosBatch(body, authorization(request)));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询视频生成任务", description = "查询单个或批量视频生成任务状态")
    public Result<Object> getVideoTask(@PathVariable String taskId, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getVideoTask(taskId, authorization(request)));
    }

    private String authorization(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return authorization;
    }
}
