package com.example.appbackend.controller;

import com.example.appbackend.dto.ActivityNoticeRequest;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ActivityNotice;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.ActivityNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activity-notices")
@Tag(name = "活动通知管理", description = "活动通知的增删改查接口")
public class ActivityNoticeController {

    @Autowired
    private ActivityNoticeService activityNoticeService;

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

    @GetMapping
    @Operation(summary = "获取通知列表", description = "分页获取通知列表，支持按活动ID、标题、状态筛选")
    public Result<PageResponse<ActivityNotice>> getNoticeList(
            @Parameter(description = "活动ID")
            @RequestParam(required = false) Long activityId,
            @Parameter(description = "通知标题（模糊查询）")
            @RequestParam(required = false) String title,
            @Parameter(description = "通知状态: DRAFT-草稿, PUBLISHED-已发布")
            @RequestParam(required = false) String status,
            @Parameter(description = "页码，默认1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，默认10")
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResponse<ActivityNotice> result = activityNoticeService.getNoticeList(activityId, title, status, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取通知详情", description = "根据通知ID获取通知详细信息")
    public Result<ActivityNotice> getNoticeById(
            @Parameter(description = "通知ID", required = true)
            @PathVariable Long id) {
        ActivityNotice notice = activityNoticeService.getNoticeById(id);
        return Result.success(notice);
    }

    @PostMapping
    @Operation(summary = "创建通知", description = "创建新通知，需要教师或管理员权限")
    public Result<ActivityNotice> createNotice(
            @Valid @RequestBody ActivityNoticeRequest request,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        String username = (String) httpRequest.getAttribute("username");
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(Result.UNAUTHORIZED_CODE, "用户不存在"));
        ActivityNotice notice = activityNoticeService.createNotice(request, user.getId(), user.getRealName());
        return Result.success("通知创建成功", notice);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新通知", description = "更新指定ID的通知信息")
    public Result<ActivityNotice> updateNotice(
            @Parameter(description = "通知ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ActivityNoticeRequest request,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        ActivityNotice notice = activityNoticeService.updateNotice(id, request);
        return Result.success("通知更新成功", notice);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除通知", description = "删除指定ID的通知")
    public Result<Void> deleteNotice(
            @Parameter(description = "通知ID", required = true)
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        activityNoticeService.deleteNotice(id);
        return Result.success("通知删除成功", null);
    }

    @GetMapping("/activity/{activityId}")
    @Operation(summary = "获取活动的通知列表", description = "查看某个活动的所有通知")
    public Result<PageResponse<ActivityNotice>> getNoticesByActivity(
            @Parameter(description = "活动ID", required = true)
            @PathVariable Long activityId,
            @Parameter(description = "页码，默认1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，默认10")
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResponse<ActivityNotice> result = activityNoticeService.getNoticesByActivityId(activityId, pageNum, pageSize);
        return Result.success(result);
    }
}
