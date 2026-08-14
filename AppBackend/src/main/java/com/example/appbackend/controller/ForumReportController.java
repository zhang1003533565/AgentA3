package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ForumReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forum/reports")
@Tag(name = "论坛举报管理", description = "论坛举报列表、详情、处理与统计接口")
public class ForumReportController {

    private static final String ROLE_ADMIN = "ADMIN";

    @Autowired
    private ForumReportService forumReportService;

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
    @Operation(summary = "提交举报", description = "登录用户举报帖子或评论")
    public Result<ForumReportResponse> createReport(
            HttpServletRequest request,
            @Valid @RequestBody ForumReportCreateRequest createRequest) {
        Long reporterId = getCurrentUserId(request);
        if (reporterId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success("举报已提交", forumReportService.createReport(createRequest, reporterId));
    }

    @Operation(summary = "获取举报列表", description = "管理员分页查看举报列表，支持按状态和目标类型筛选")    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @GetMapping
    public Result<PageResponse<ForumReportResponse>> getReports(
            HttpServletRequest request,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "处理状态：0-待处理，1-已处理，2-已驳回", example = "0")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "举报目标类型：1-帖子，2-评论", example = "1")
            @RequestParam(required = false) Integer targetType) {
        checkAdminRole(request);
        return Result.success(forumReportService.getReports(page, size, status, targetType));
    }

    @Operation(summary = "批量删除举报", description = "管理员批量删除举报记录及其审计日志")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @DeleteMapping("/batch")
    public Result<Void> deleteReports(
            HttpServletRequest request,
            @RequestBody List<Long> ids) {
        checkAdminRole(request);
        forumReportService.deleteReports(ids);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "获取举报详情", description = "管理员查看指定举报详情")
    @GetMapping("/{id}")
    public Result<ForumReportResponse> getReportDetail(
            HttpServletRequest request,
            @Parameter(description = "举报ID", required = true, example = "1")
            @PathVariable Long id) {
        checkAdminRole(request);
        return Result.success(forumReportService.getReportDetail(id));
    }

    @Operation(summary = "处理举报", description = "管理员处理举报，支持 IGNORE 或 DELETE_CONTENT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "处理成功"),
            @ApiResponse(responseCode = "400", description = "参数错误或重复处理"),
            @ApiResponse(responseCode = "403", description = "无权限"),
            @ApiResponse(responseCode = "404", description = "举报记录不存在")
    })
    @PutMapping("/{id}/handle")
    public Result<ForumReportResponse> handleReport(
            HttpServletRequest request,
            @Parameter(description = "举报ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ForumReportHandleRequest handleRequest) {
        checkAdminRole(request);
        Long handlerId = (Long) request.getAttribute("userId");
        return Result.success(forumReportService.handleReport(id, handleRequest, handlerId));
    }

    @Operation(summary = "获取举报统计", description = "管理员查看举报总数、待处理、已处理、已驳回和类型统计")
    @GetMapping("/statistics")
    public Result<ForumReportStatisticsResponse> getStatistics(HttpServletRequest request) {
        checkAdminRole(request);
        return Result.success(forumReportService.getStatistics());
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "获取举报审计日志", description = "管理员查看指定举报的创建和处理记录")
    public Result<List<ForumReportAuditLogResponse>> getAuditLogs(
            HttpServletRequest request,
            @PathVariable Long id) {
        checkAdminRole(request);
        return Result.success(forumReportService.getAuditLogs(id));
    }
}
