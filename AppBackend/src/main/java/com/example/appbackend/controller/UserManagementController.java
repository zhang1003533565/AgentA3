package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户列表查询、更新、启用/禁用、重置密码等管理接口（需要管理员权限）")
public class UserManagementController {

    @Autowired
    private UserService userService;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!ROLE_ADMIN.equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员可执行");
        }
    }

    @Operation(summary = "获取用户列表", description = "分页获取用户列表，支持按用户名、角色、状态筛选，需要管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @GetMapping
    public Result<PageResponse<UserListItem>> getUserList(
            HttpServletRequest request,
            @Parameter(description = "页码，从1开始", example = "1") 
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10") 
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "用户名（模糊查询）", example = "张") 
            @RequestParam(required = false) String username,
            @Parameter(description = "角色: STUDENT-学生, TEACHER-教师, ADMIN-管理员", example = "STUDENT") 
            @RequestParam(required = false) String role,
            @Parameter(description = "状态: 0-禁用, 1-启用", example = "1") 
            @RequestParam(required = false) Integer status) {
        checkAdminRole(request);
        PageResponse<UserListItem> result = userService.getUserList(page, size, username, role, status);
        return Result.success(result);
    }

    @Operation(summary = "更新用户信息", description = "更新指定用户的信息，需要管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PutMapping("/{id}")
    public Result<Void> updateUser(
            HttpServletRequest request, 
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long id, 
            @Parameter(description = "用户更新信息", required = true) 
            @Valid @RequestBody UserUpdateRequest requestBody) {
        checkAdminRole(request);
        userService.updateUser(id, requestBody);
        return Result.success();
    }

    @Operation(summary = "启用用户", description = "启用指定用户账号，需要管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "启用成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(
            HttpServletRequest request, 
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long id) {
        checkAdminRole(request);
        userService.enableUser(id);
        return Result.success();
    }

    @Operation(summary = "禁用用户", description = "禁用指定用户账号，需要管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "禁用成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(
            HttpServletRequest request, 
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long id) {
        checkAdminRole(request);
        userService.disableUser(id);
        return Result.success();
    }

    @Operation(summary = "重置用户密码", description = "重置指定用户的密码，需要管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "重置成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(
            HttpServletRequest request, 
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long id, 
            @Parameter(description = "新密码信息", required = true) 
            @Valid @RequestBody PasswordResetRequest requestBody) {
        checkAdminRole(request);
        userService.resetPassword(id, requestBody.getNewPassword());
        return Result.success();
    }
}
