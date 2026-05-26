package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.SystemConfigDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.SystemConfigAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system-config")
@Tag(name = "系统配置", description = "后台系统配置与连通测试接口")
public class SystemConfigController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final SystemConfigAdminService systemConfigAdminService;

    public SystemConfigController(SystemConfigAdminService systemConfigAdminService) {
        this.systemConfigAdminService = systemConfigAdminService;
    }

    private boolean isAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        return ROLE_ADMIN.equals(role);
    }

    @GetMapping("/list")
    @Operation(summary = "系统配置列表", description = "管理员权限")
    public Result<PageResponse<SystemConfigDTO.ConfigVO>> list(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "配置分组") @RequestParam(required = false) String group,
            @Parameter(description = "配置键前缀，多个用逗号分隔") @RequestParam(required = false) String prefixes,
            HttpServletRequest request) {
        if (!isAdmin(request)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(systemConfigAdminService.list(current, size, keyword, group, prefixes));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新系统配置", description = "管理员权限")
    public Result<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody SystemConfigDTO.UpdateRequest req,
            HttpServletRequest request) {
        if (!isAdmin(request)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        systemConfigAdminService.update(id, req);
        return Result.success("更新成功", (Void) null);
    }

    @PostMapping("/upsert")
    @Operation(summary = "按配置键保存系统配置", description = "管理员权限；存在则更新，不存在则创建")
    public Result<SystemConfigDTO.ConfigVO> upsert(
            @Valid @RequestBody SystemConfigDTO.UpsertRequest req,
            HttpServletRequest request) {
        if (!isAdmin(request)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(systemConfigAdminService.upsert(req));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "测试配置连通性", description = "管理员权限")
    public Result<SystemConfigDTO.TestResultVO> test(
            @PathVariable Long id,
            HttpServletRequest request) {
        if (!isAdmin(request)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(systemConfigAdminService.test(id));
    }
}
