package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.NavigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/map/navigation")
@Tag(name = "路线导航", description = "发起导航、获取路线、到达确认、历史记录接口")
public class NavigationController {

    @Autowired
    private NavigationService navigationService;

    @PostMapping
    @Operation(summary = "发起导航", description = "创建导航记录并返回路线信息")
    public Result<NavigationResponse> startNavigation(
            @Valid @RequestBody NavigationRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        NavigationResponse result = navigationService.startNavigation(request, userId);
        return Result.success(result);
    }

    @GetMapping("/route")
    @Operation(summary = "获取导航路线", description = "获取起点到终点的路线（不创建导航记录）")
    public Result<NavigationRouteResponse> getRoute(
            @Parameter(description = "起点经度", required = true)
            @RequestParam BigDecimal fromLongitude,
            @Parameter(description = "起点纬度", required = true)
            @RequestParam BigDecimal fromLatitude,
            @Parameter(description = "终点经度", required = true)
            @RequestParam BigDecimal toLongitude,
            @Parameter(description = "终点纬度", required = true)
            @RequestParam BigDecimal toLatitude,
            @Parameter(description = "出行方式：walking（步行，默认）/ driving（驾车）")
            @RequestParam(required = false) String mode) {
        NavigationRouteResponse result = navigationService.getRoute(
                fromLongitude, fromLatitude, toLongitude, toLatitude, mode);
        return Result.success(result);
    }

    @PostMapping("/{navigationId}/arrive")
    @Operation(summary = "到达确认", description = "确认已到达导航目的地")
    public Result<Void> arriveConfirm(
            @PathVariable Long navigationId,
            HttpServletRequest httpRequest) {
        navigationService.arriveConfirm(navigationId);
        return Result.success("到达确认成功", null);
    }

    @PostMapping("/{navigationId}/cancel")
    @Operation(summary = "取消导航", description = "取消当前导航")
    public Result<Void> cancelNavigation(
            @PathVariable Long navigationId,
            HttpServletRequest httpRequest) {
        navigationService.cancelNavigation(navigationId);
        return Result.success("导航已取消", null);
    }

    @GetMapping("/history")
    @Operation(summary = "获取导航历史", description = "获取用户导航历史记录")
    public Result<PageResponse<NavigationHistoryItem>> getNavigationHistory(
            @Parameter(description = "页码，默认1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，默认10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        PageResponse<NavigationHistoryItem> result = navigationService.getNavigationHistory(userId, pageNum, pageSize);
        return Result.success(result);
    }
}
