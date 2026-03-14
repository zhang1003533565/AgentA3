package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
@Tag(name = "报名管理", description = "报名接口")
public class RegistrationController {

    @Operation(summary = "报名活动", description = "报名活动（学生）")
    @PostMapping
    public Result<Void> registerActivity() {
        return Result.success();
    }

    @Operation(summary = "取消报名", description = "取消报名")
    @DeleteMapping("/{id}")
    public Result<Void> cancelRegistration(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "我的报名列表", description = "获取当前用户的报名列表")
    @GetMapping("/my-registrations")
    public Result<Object> getMyRegistrations() {
        return Result.success();
    }

    @Operation(summary = "报名名单", description = "获取活动的报名名单（教师/管理员）")
    @GetMapping("/activities/{activityId}/registrations")
    public Result<Object> getRegistrations(@PathVariable Long activityId) {
        return Result.success();
    }

    @Operation(summary = "审核报名", description = "审核报名（教师/管理员）")
    @PutMapping("/{id}/audit")
    public Result<Void> auditRegistration(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "批量审核报名", description = "批量审核报名（教师/管理员）")
    @PutMapping("/batch-audit")
    public Result<Void> batchAuditRegistration() {
        return Result.success();
    }
}
