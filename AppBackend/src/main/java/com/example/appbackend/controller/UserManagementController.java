package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户管理接口")
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

    @Operation(summary = "用户列表", description = "获取用户列表（管理员）")
    @GetMapping
    public Result<PageResponse<UserListItem>> getUserList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        checkAdminRole(request);
        PageResponse<UserListItem> result = userService.getUserList(page, size, username, role, status);
        return Result.success(result);
    }

    @Operation(summary = "更新用户", description = "更新用户信息（管理员）")
    @PutMapping("/{id}")
    public Result<Void> updateUser(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody UserUpdateRequest requestBody) {
        checkAdminRole(request);
        userService.updateUser(id, requestBody);
        return Result.success();
    }

    @Operation(summary = "启用用户", description = "启用用户（管理员）")
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(HttpServletRequest request, @PathVariable Long id) {
        checkAdminRole(request);
        userService.enableUser(id);
        return Result.success();
    }

    @Operation(summary = "禁用用户", description = "禁用用户（管理员）")
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(HttpServletRequest request, @PathVariable Long id) {
        checkAdminRole(request);
        userService.disableUser(id);
        return Result.success();
    }

    @Operation(summary = "重置密码", description = "重置用户密码（管理员）")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody PasswordResetRequest requestBody) {
        checkAdminRole(request);
        userService.resetPassword(id, requestBody.getNewPassword());
        return Result.success();
    }
}
