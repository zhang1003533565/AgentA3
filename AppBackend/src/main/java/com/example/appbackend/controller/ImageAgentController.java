package com.example.appbackend.controller;

import com.example.appbackend.dto.ImageGenerationDTO;
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

@RestController
@RequestMapping("/api/ai/images")
@Tag(name = "图片智能体", description = "代理 Python FastAPI 的 Qwen 图片生成智能体接口")
public class ImageAgentController {

    private final PythonAiProxyService pythonAiProxyService;

    public ImageAgentController(PythonAiProxyService pythonAiProxyService) {
        this.pythonAiProxyService = pythonAiProxyService;
    }

    @PostMapping("/generate")
    @Operation(summary = "单张生成图片", description = "调用图片智能体生成单张文生图，返回任务状态、图片 URL/Base64 和生成参数")
    public Result<Object> generateImage(@RequestBody ImageGenerationDTO.GenerateRequest body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.generateImage(body.toMap(), authorization(request)));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量生成图片", description = "调用图片智能体批量生成文生图，返回每张图片的状态和结果")
    public Result<Object> generateImagesBatch(@RequestBody ImageGenerationDTO.GenerateRequest body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.generateImagesBatch(body.toMap(), authorization(request)));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询图片生成任务", description = "查询单张或批量图片生成任务状态")
    public Result<Object> getImageTask(@PathVariable String taskId, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getImageTask(taskId, authorization(request)));
    }

    private String authorization(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return authorization;
    }
}
