package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.SignInListItem;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.SignIn;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.SignInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signins")
@Tag(name = "签到管理", description = "签到接口")
public class SignInController {

    @Autowired
    private SignInService signInService;

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

    @Operation(summary = "发布签到", description = "老师发布签到")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "发布成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PostMapping("/open")
    public Result<Void> openSignIn(
            @Parameter(description = "活动ID", required = true, example = "1")
            @RequestParam Long activityId,
            HttpServletRequest request) {
        checkRole(request);
        signInService.openSignIn(activityId);
        return Result.success();
    }

    @Operation(summary = "结束签到", description = "老师结束签到")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "结束成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PostMapping("/close")
    public Result<Void> closeSignIn(
            @Parameter(description = "活动ID", required = true, example = "1")
            @RequestParam Long activityId,
            HttpServletRequest request) {
        checkRole(request);
        signInService.closeSignIn(activityId);
        return Result.success();
    }

    @Operation(summary = "检查签到是否开启", description = "检查活动签到是否开启")
    @GetMapping("/activity/{activityId}/status")
    public Result<Boolean> getSignInOpenStatus(
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long activityId) {
        boolean isOpen = signInService.isSignInOpen(activityId);
        return Result.success(isOpen);
    }

    @Operation(summary = "签到", description = "学生签到")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "签到成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "活动未开始或已结束")
    })
    @PostMapping("/{activityId}")
    public Result<SignIn> signIn(
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long activityId,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        SignIn signIn = signInService.signIn(activityId, user.getId());
        return Result.success(signIn);
    }

    @Operation(summary = "签到列表", description = "获取活动签到列表（教师/管理员）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @GetMapping("/activities/{activityId}/signins")
    public Result<PageResponse<SignInListItem>> getSignIns(
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long activityId,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        checkRole(request);
        PageResponse<SignInListItem> list = signInService.getActivitySignIns(activityId, page, size);
        return Result.success(list);
    }

    @Operation(summary = "补签", description = "补签（根据活动ID和学生ID，教师）")
    @PostMapping("/activity/{activityId}/student/{studentId}/supplement")
    public Result<SignIn> supplementSignInByActivityAndUser(
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long activityId,
            @Parameter(description = "学生ID", required = true, example = "1")
            @PathVariable Long studentId,
            HttpServletRequest request) {
        checkRole(request);
        User user = getCurrentUser(request);
        SignIn signIn = signInService.supplementSignInByActivityAndUser(activityId, studentId, user.getId());
        return Result.success(signIn);
    }

    @Operation(summary = "检查签到状态", description = "检查签到状态（学生）")
    @GetMapping("/activities/{activityId}/signin-status")
    public Result<SignIn> getSignInStatus(
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long activityId,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        SignIn signIn = signInService.getSignInStatus(activityId, user.getId());
        return Result.success(signIn);
    }

    @Operation(summary = "签到后复核并发放学分", description = "教师/管理员复核签到记录，复核通过即发放学分")
    @PutMapping("/{id}/review")
    public Result<Void> reviewSignIn(
            @Parameter(description = "签到记录ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "复核状态 APPROVED/REJECTED", required = true, example = "APPROVED")
            @RequestParam String reviewStatus,
            @Parameter(description = "复核备注")
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        checkRole(request);
        User user = getCurrentUser(request);
        signInService.reviewSignInAndGrantCredit(id, reviewStatus, user.getId(), remark);
        return Result.success();
    }

    @Operation(summary = "批量签到后复核并发放学分", description = "教师/管理员批量复核签到记录，复核通过即发放学分")
    @PutMapping("/batch-review")
    public Result<Void> batchReviewSignIn(
            @Parameter(description = "签到记录ID数组", required = true)
            @RequestParam Long[] signInIds,
            @Parameter(description = "复核状态 APPROVED/REJECTED", required = true, example = "APPROVED")
            @RequestParam String reviewStatus,
            @Parameter(description = "复核备注")
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        checkRole(request);
        User user = getCurrentUser(request);
        signInService.batchReviewSignInAndGrantCredit(signInIds, reviewStatus, user.getId(), remark);
        return Result.success();
    }
}
