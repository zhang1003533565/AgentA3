package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
@Tag(name = "活动管理", description = "活动接口")
public class ActivityController {

    @Operation(summary = "活动列表", description = "获取活动列表")
    @GetMapping
    public Result<Object> getActivityList() {
        return Result.success();
    }

    @Operation(summary = "活动详情", description = "获取活动详情")
    @GetMapping("/{id}")
    public Result<Object> getActivityDetail(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "创建活动", description = "创建活动（教师/管理员）")
    @PostMapping
    public Result<Void> createActivity() {
        return Result.success();
    }

    @Operation(summary = "更新活动", description = "更新活动（教师/管理员）")
    @PutMapping("/{id}")
    public Result<Void> updateActivity(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "删除活动", description = "删除活动（教师/管理员）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "上下架活动", description = "上下架活动（教师/管理员）")
    @PutMapping("/{id}/status")
    public Result<Void> updateActivityStatus(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "提交审核", description = "提交活动审核（教师）")
    @PostMapping("/{id}/submit")
    public Result<Void> submitActivity(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "审核活动", description = "审核活动（管理员）")
    @PostMapping("/{id}/audit")
    public Result<Void> auditActivity(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "获取热门活动", description = "获取热门活动列表")
    @GetMapping("/hot")
    public Result<Object> getHotActivities() {
        return Result.success();
    }

    @Operation(summary = "发布活动通知", description = "发布活动通知（教师）")
    @PostMapping("/{activityId}/notices")
    public Result<Void> publishNotice(@PathVariable Long activityId) {
        return Result.success();
    }
}
