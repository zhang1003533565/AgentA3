package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户管理接口")
public class UserManagementController {

    @Autowired
    private UserService userService;

    @Operation(summary = "用户列表", description = "获取用户列表（管理员）")
    @GetMapping
    public Result<PageResponse<UserListItem>> getUserList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        PageResponse<UserListItem> result = userService.getUserList(page, size, username, role, status);
        return Result.success(result);
    }

    @Operation(summary = "更新用户", description = "更新用户信息（管理员）")
    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userService.updateUser(id, request);
        return Result.success();
    }

    @Operation(summary = "启用用户", description = "启用用户（管理员）")
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        userService.enableUser(id);
        return Result.success();
    }

    @Operation(summary = "禁用用户", description = "禁用用户（管理员）")
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        userService.disableUser(id);
        return Result.success();
    }

    @Operation(summary = "重置密码", description = "重置用户密码（管理员）")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return Result.success();
    }
}
