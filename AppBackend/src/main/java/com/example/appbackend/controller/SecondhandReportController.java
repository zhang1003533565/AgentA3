package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.SecondhandReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/secondhand/reports")
@Tag(name = "二手物品举报管理", description = "二手物品举报提交、查看、处理与统计接口")
public class SecondhandReportController {

    private static final String ROLE_ADMIN = "ADMIN";

    @Autowired
    private SecondhandReportService reportService;

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!ROLE_ADMIN.equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员可执行");
        }
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    @PostMapping
    @Operation(summary = "提交举报", description = "登录用户举报二手物品")
    public Result<SecondhandReportResponse> createReport(
            HttpServletRequest request,
            @Valid @RequestBody SecondhandReportCreateRequest createRequest) {
        Long reporterId = getCurrentUserId(request);
        if (reporterId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success("举报已提交", reportService.createReport(createRequest, reporterId));
    }

    @Operation(summary = "获取举报列表", description = "管理员分页查看二手物品举报列表，支持按状态筛选")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @GetMapping
    public Result<Page<SecondhandReportResponse>> getReports(
            HttpServletRequest request,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "处理状态：0-待处理，1-已处理，2-已驳回", example = "0")
            @RequestParam(required = false) Integer status) {
        checkAdminRole(request);
        return Result.success(reportService.getReports(page, size, status));
    }

    @Operation(summary = "获取举报详情", description = "管理员查看指定举报详情")
    @GetMapping("/{id}")
    public Result<SecondhandReportResponse> getReportDetail(
            HttpServletRequest request,
            @Parameter(description = "举报ID", required = true, example = "1")
            @PathVariable Long id) {
        checkAdminRole(request);
        return Result.success(reportService.getReportDetail(id));
    }

    @Operation(summary = "处理举报", description = "管理员处理举报，支持 IGNORE 或 OFFLINE_ITEM")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "处理成功"),
            @ApiResponse(responseCode = "400", description = "参数错误或重复处理"),
            @ApiResponse(responseCode = "403", description = "无权限"),
            @ApiResponse(responseCode = "404", description = "举报记录不存在")
    })
    @PutMapping("/{id}/handle")
    public Result<SecondhandReportResponse> handleReport(
            HttpServletRequest request,
            @Parameter(description = "举报ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody SecondhandReportHandleRequest handleRequest) {
        checkAdminRole(request);
        Long handlerId = (Long) request.getAttribute("userId");
        return Result.success(reportService.handleReport(id, handleRequest, handlerId));
    }

    @Operation(summary = "获取举报统计", description = "管理员查看举报总数、待处理、已处理和已驳回统计")
    @GetMapping("/statistics")
    public Result<SecondhandReportStatisticsResponse> getStatistics(HttpServletRequest request) {
        checkAdminRole(request);
        return Result.success(reportService.getStatistics());
    }
}
