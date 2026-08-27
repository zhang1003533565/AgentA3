package com.example.appbackend.controller;

import com.example.appbackend.dto.AgentModelBindDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AgentModelBindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Agent Model Bind Management Controller
 * 管理后台接口：智能体与模型绑定管理
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai/bind")
@Tag(name = "智能体 - 模型绑定管理", description = "管理智能体与 AI 模型的绑定关系")
@RequiredArgsConstructor
public class AgentModelBindAdminController {

    private final AgentModelBindService agentModelBindService;

    /**
     * 绑定智能体到模型配置
     * 一个智能体只能绑定一套模型（唯一约束 uk_agent）
     */
    @PostMapping("/bind")
    @Operation(summary = "绑定智能体到模型配置")
    public Result<?> bind(
            @RequestBody AgentModelBindDTO.BindRequest request,
            HttpServletRequest httpRequest) {
        
        requireAdmin(httpRequest);
        try {
            var result = agentModelBindService.bindAgent(request);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据 agentId 获取绑定信息和完整模型配置
     */
    @GetMapping("/agent/{agentId}")
    @Operation(summary = "获取智能体的模型绑定配置")
    public Result<?> getByAgentId(
            @PathVariable String agentId,
            HttpServletRequest httpRequest) {
        
        requireAdmin(httpRequest);
        try {
            var result = agentModelBindService.getBindInfoByAgent(agentId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解除智能体绑定
     */
    @DeleteMapping("/unbind/{agentId}")
    @Operation(summary = "解除智能体模型绑定")
    public Result<?> unbind(
            @PathVariable String agentId,
            HttpServletRequest httpRequest) {
        
        requireAdmin(httpRequest);
        try {
            agentModelBindService.unbindAgent(agentId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限访问");
        }
    }
}
