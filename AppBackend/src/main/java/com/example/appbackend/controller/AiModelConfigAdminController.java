package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AiModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI Model Config Management Controller
 * 管理后台接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai/model-config")
@Tag(name = "AI 模型配置管理", description = "管理后台配置 DeepSeek 等模型参数")
@RequiredArgsConstructor
public class AiModelConfigAdminController {

    private final AiModelConfigService aiModelConfigService;

    /**
     * 新增模型配置
     */
    @PostMapping
    @Operation(summary = "新增模型配置")
    public Result<AiModelConfigDTO.ConfigVO> create(
            @Valid @RequestBody AiModelConfigDTO.CreateRequest request,
            HttpServletRequest httpRequest) {
        
        requireAdmin(httpRequest);
        return Result.success(aiModelConfigService.createConfig(request));
    }

    /**
     * 更新模型配置
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新模型配置")
    public Result<AiModelConfigDTO.ConfigVO> update(
            @PathVariable Long id,
            @Valid @RequestBody AiModelConfigDTO.UpdateRequest request,
            HttpServletRequest httpRequest) {
        
        requireAdmin(httpRequest);
        request.setId(id);
        return Result.success(aiModelConfigService.updateConfig(request));
    }

    /**
     * 获取所有模型配置列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有模型配置")
    public Result<?> getAll(HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return Result.success(aiModelConfigService.getAllConfigs());
    }

    /**
     * 获取单个配置
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取单个配置")
    public Result<?> getById(@PathVariable Long id, HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return Result.success(aiModelConfigService.getConfigById(id));
    }

    /**
     * 获取启用的配置列表（用于下拉选择）
     */
    @GetMapping("/enabled")
    @Operation(summary = "获取启用的配置")
    public Result<?> getEnabled(HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return Result.success(aiModelConfigService.getEnabledConfigs());
    }

    private void requireAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限访问");
        }
    }
}
