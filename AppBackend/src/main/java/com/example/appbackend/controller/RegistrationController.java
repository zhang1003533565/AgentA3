package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.RegistrationListItem;
import com.example.appbackend.entity.Registration;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
@Tag(name = "报名管理", description = "活动报名、取消报名、报名审核等接口")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    private void checkRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员、教师可执行");
        }
    }

    private User getCurrentUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
    }

    @Operation(summary = "报名活动", description = "学生报名参加活动")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "报名成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "报名已结束或名额已满")
    })
    @PostMapping
    public Result<Registration> registerActivity(
            @Parameter(description = "活动ID", required = true, example = "1")
            @RequestParam Long activityId,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        Registration registration = registrationService.registerActivity(activityId, user.getId());
        return Result.success(registration);
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
            @PathVariable Long id,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        registrationService.cancelRegistration(id, user.getId());
        return Result.success();
    }

    @Operation(summary = "我的报名列表", description = "获取当前用户的报名记录列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/my-registrations")
    public Result<PageResponse<Registration>> getMyRegistrations(
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        PageResponse<Registration> list = registrationService.getMyRegistrations(user.getId(), page, size);
        return Result.success(list);
    }

    @Operation(summary = "活动报名名单", description = "获取指定活动的报名名单，需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @GetMapping("/activities/{activityId}/registrations")
    public Result<PageResponse<RegistrationListItem>> getRegistrations(
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long activityId,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        checkRole(request);
        PageResponse<RegistrationListItem> list = registrationService.getActivityRegistrations(activityId, page, size);
        return Result.success(list);
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
            @PathVariable Long id,
            @Parameter(description = "审核状态: APPROVED-通过, REJECTED-拒绝", required = true, example = "APPROVED")
            @RequestParam String auditStatus,
            @Parameter(description = "审核备注")
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        checkRole(request);
        User user = getCurrentUser(request);
        registrationService.auditRegistration(id, auditStatus, user.getId(), remark);
        return Result.success();
    }

    @Operation(summary = "批量审核报名", description = "批量审核报名申请，需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "审核成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PutMapping("/batch-audit")
    public Result<Void> batchAuditRegistration(
            @Parameter(description = "报名记录ID数组", required = true)
            @RequestParam Long[] registrationIds,
            @Parameter(description = "审核状态: APPROVED-通过, REJECTED-拒绝", required = true, example = "APPROVED")
            @RequestParam String auditStatus,
            @Parameter(description = "审核备注")
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        checkRole(request);
        User user = getCurrentUser(request);
        registrationService.batchAuditRegistration(registrationIds, auditStatus, user.getId(), remark);
        return Result.success();
    }
}
