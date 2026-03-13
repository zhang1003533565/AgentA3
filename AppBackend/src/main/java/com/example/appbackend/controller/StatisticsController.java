package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics")
@Tag(name = "统计管理", description = "统计接口")
public class StatisticsController {

    @Operation(summary = "仪表盘统计", description = "获取仪表盘统计数据（管理员）")
    @GetMapping("/dashboard")
    public Result<Object> getDashboard() {
        return Result.success();
    }

    @Operation(summary = "活动统计", description = "获取活动统计数据（教师）")
    @GetMapping("/activities")
    public Result<Object> getActivityStatistics() {
        return Result.success();
    }

    @Operation(summary = "分类统计", description = "获取分类统计数据（管理员）")
    @GetMapping("/categories")
    public Result<Object> getCategoryStatistics() {
        return Result.success();
    }

    @Operation(summary = "报名趋势", description = "获取报名趋势数据（管理员）")
    @GetMapping("/registration-trend")
    public Result<Object> getRegistrationTrend() {
        return Result.success();
    }
}
