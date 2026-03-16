package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
@Tag(name = "报名管理", description = "活动报名、取消报名、报名审核等接口")
public class RegistrationController {

    @Operation(summary = "报名活动", description = "学生报名参加活动")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "报名成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "报名已结束或名额已满")
    })
    @PostMapping
    public Result<Void> registerActivity() {
        return Result.success();
    }

    @Operation(summary = "取消报名", description = "取消已报名的活动")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "取消成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "报名记录不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> cancelRegistration(
            @Parameter(description = "报名记录ID", required = true, example = "1") 
            @PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "我的报名列表", description = "获取当前用户的报名记录列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/my-registrations")
    public Result<Object> getMyRegistrations() {
        return Result.success();
    }

    @Operation(summary = "活动报名名单", description = "获取指定活动的报名名单，需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @GetMapping("/activities/{activityId}/registrations")
    public Result<Object> getRegistrations(
            @Parameter(description = "活动ID", required = true, example = "1") 
            @PathVariable Long activityId) {
        return Result.success();
    }

    @Operation(summary = "审核报名", description = "审核报名申请（通过/拒绝），需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "审核成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "报名记录不存在")
    })
    @PutMapping("/{id}/audit")
    public Result<Void> auditRegistration(
            @Parameter(description = "报名记录ID", required = true, example = "1") 
            @PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "批量审核报名", description = "批量审核报名申请，需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "审核成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PutMapping("/batch-audit")
    public Result<Void> batchAuditRegistration() {
        return Result.success();
    }
}
