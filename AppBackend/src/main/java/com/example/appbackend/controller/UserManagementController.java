package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户管理接口")
public class UserManagementController {

    @Operation(summary = "用户列表", description = "获取用户列表（管理员）")
    @GetMapping
    public Result<Object> getUserList() {
        return Result.success();
    }

    @Operation(summary = "更新用户", description = "更新用户信息（管理员）")
    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "启用用户", description = "启用用户（管理员）")
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "禁用用户", description = "禁用用户（管理员）")
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "重置密码", description = "重置用户密码（管理员）")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        return Result.success();
    }
}
