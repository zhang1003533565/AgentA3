package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MapStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/map/statistics")
@Tag(name = "地图数据统计", description = "导航统计、设施热度、标记访问统计接口（仅管理员）")
public class MapStatisticsController {

    @Autowired
    private MapStatisticsService mapStatisticsService;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作");
        }
    }

    @GetMapping("/navigation")
    @Operation(summary = "导航统计", description = "获取导航总览统计数据（仅管理员）")
    public Result<NavigationStatisticsResponse> getNavigationStatistics(
            @Parameter(description = "开始日期，格式：yyyy-MM-dd")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式：yyyy-MM-dd")
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        NavigationStatisticsResponse result = mapStatisticsService.getNavigationStatistics(startDate, endDate);
        return Result.success(result);
    }

    @GetMapping("/facility-heat")
    @Operation(summary = "设施热度统计", description = "获取设施访问和导航热度排名（仅管理员）")
    public Result<List<MarkerSummaryItem>> getFacilityHeat(
            @Parameter(description = "设施类型")
            @RequestParam(required = false) Integer facilityType,
            @Parameter(description = "开始日期，格式：yyyy-MM-dd")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式：yyyy-MM-dd")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "返回数量，默认10")
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        List<MarkerSummaryItem> result = mapStatisticsService.getFacilityHeat(facilityType, startDate, endDate, limit);
        return Result.success(result);
    }

    @GetMapping("/marker-visit")
    @Operation(summary = "标记访问统计", description = "获取指定标记的访问统计（仅管理员）")
    public Result<MarkerVisitResponse> getMarkerVisit(
            @Parameter(description = "标记ID", required = true)
            @RequestParam Long markerId,
            @Parameter(description = "查询日期，格式：yyyy-MM-dd")
            @RequestParam(required = false) String date,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        MarkerVisitResponse result = mapStatisticsService.getMarkerVisit(markerId, date);
        return Result.success(result);
    }
}
