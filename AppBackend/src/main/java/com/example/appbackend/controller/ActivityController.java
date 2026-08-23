package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.ActivityService;
import com.example.appbackend.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@Tag(name = "活动管理", description = "活动的增删改查、审核、上下架等接口")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private UserRepository userRepository;


    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    /**
     * 统一权限检查方法
     * 检查用户是否为管理员或教师
     */
    private void checkRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员、教师可执行");
        }
    }
    

    @Operation(summary = "获取活动列表", description = "分页获取活动列表，支持按标题、分类、状态筛选")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取活动列表"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @GetMapping
    public Result<PageResponse<Activity>> getActivityList(
            @Parameter(description = "页码，从1开始", example = "1") 
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10") 
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "活动标题（模糊查询）", example = "讲座") 
            @RequestParam(required = false) String title,
            @Parameter(description = "分类ID", example = "1") 
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "活动状态: DRAFT-草稿, PUBLISHED-已发布, REJECTED-已驳回, CANCELLED-已取消, COMPLETED-已完成", example = "PUBLISHED")
            @RequestParam(required = false) String status,
            @Parameter(description = "时间阶段: upcoming-即将开始, ongoing-进行中, ended-已结束（可选）", example = "upcoming")
            @RequestParam(required = false) String timePhase) {
        Status statusEnum = status != null ? Status.valueOf(status) : null;
        PageResponse<Activity> list = activityService.getActivityList(page, size, title, categoryId, statusEnum, timePhase);
        return Result.success(list);
    }

    @Operation(summary = "我发起的活动", description = "获取当前用户发起的活动（分页）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/mine")
    public Result<PageResponse<Activity>> getMyActivities(
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        PageResponse<Activity> list = activityService.getActivitiesByOrganizer(userId, page, size);
        return Result.success(list);
    }

    @Operation(summary = "获取活动详情", description = "根据活动ID获取活动详细信息")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取活动详情"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @GetMapping("/{id}")
    public Result<Activity> getActivityDetail(
            @Parameter(description = "活动ID", required = true, example = "1") 
            @PathVariable Long id) {
        Activity activity = activityService.getActivityById(id);
        return Result.success(activity);
    }

    @Operation(summary = "创建活动", description = "创建新活动，需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PostMapping
    public Result<Activity> draftActivity(
            @Parameter(description = "活动信息", required = true) 
            @RequestBody Activity activity, 
            HttpServletRequest request) {
        checkRole(request);
        String username = (String) request.getAttribute("username");
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(Result.UNAUTHORIZED_CODE, "用户不存在"));
        Activity created = activityService.draftActivity(activity, user.getId(), user.getRealName());
        return Result.success(created);
    }

    @Operation(summary = "发布活动", description = "发布活动，将活动状态从草稿变为已发布，需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "发布成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @PostMapping("publish/{id}")
    public Result<Activity> publishActivity(
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        checkRole(request);
        activityService.publishActivity(id);
        return Result.success();
    }

    @Operation(summary = "更新活动", description = "更新活动信息，需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @PutMapping("/{id}")
    public Result<Activity> updateActivity(
            HttpServletRequest request, 
            @Parameter(description = "活动ID", required = true, example = "1") 
            @PathVariable Long id, 
            @Parameter(description = "活动信息", required = true) 
            @RequestBody Activity activity) {
        checkRole(request);
        Activity updated = activityService.updateActivity(id, activity);
        return Result.success(updated);
    }

    @Operation(summary = "删除活动", description = "删除活动，管理员可删除所有，教师仅可删除草稿状态的活动")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> deleteActivity(
            HttpServletRequest request, 
            @Parameter(description = "活动ID", required = true, example = "1") 
            @PathVariable Long id) {
        checkRole(request);
        String role = (String) request.getAttribute("role");
        activityService.deleteActivity(id, ROLE_ADMIN.equals(role));
        return Result.success();
    }



    @Operation(summary = "批量删除活动", description = "批量删除活动，管理员可删除所有，教师仅可删除草稿状态的活动")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @DeleteMapping("/batch")
    public Result<Void> deleteActivities(
            HttpServletRequest request,
            @Parameter(description = "活动ID列表", required = true)
            @RequestBody List<Long> ids) {
        checkRole(request);
        String role = (String) request.getAttribute("role");
        activityService.deleteActivities(ids, ROLE_ADMIN.equals(role));
        return Result.success();
    }

    @Operation(summary = "上下架活动", description = "更新活动状态（发布/下架），需要教师或管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @PutMapping("/{id}/status")
    public Result<Void> updateActivityStatus(
            HttpServletRequest request, 
            @Parameter(description = "活动ID", required = true, example = "1") 
            @PathVariable Long id, 
            @Parameter(description = "目标状态: DRAFT-草稿, PUBLISHED-已发布, CANCELLED-已取消", required = true, example = "PUBLISHED") 
            @RequestParam String status) {
        checkRole(request);
        Status statusEnum = Status.valueOf(status);
        activityService.updateActivityStatus(id, statusEnum);
        return Result.success();
    }





    @Operation(summary = "收藏活动", description = "收藏指定活动，需要用户登录")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "收藏成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "已经收藏过该活动"),
        @ApiResponse(responseCode = "404", description = "活动不存在")
    })
    @PostMapping("/{id}/favorite")
    public Result<Void> addFavorite(
            HttpServletRequest request,
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        favoriteService.addFavorite(userId, id);
        return Result.success();
    }

    @Operation(summary = "取消收藏", description = "取消收藏指定活动，需要用户登录")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "取消收藏成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "未收藏该活动")
    })
    @DeleteMapping("/{id}/favorite")
    public Result<Void> removeFavorite(
            HttpServletRequest request,
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        favoriteService.removeFavorite(userId, id);
        return Result.success();
    }

    @Operation(summary = "获取我的收藏列表", description = "获取当前用户收藏的活动列表，需要用户登录")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/favorites")
    public Result<PageResponse<Activity>> getMyFavorites(
            HttpServletRequest request,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        PageResponse<Activity> favorites = favoriteService.getUserFavorites(userId, page, size);
        return Result.success(favorites);
    }

    @Operation(summary = "检查是否已收藏", description = "检查当前用户是否已收藏指定活动，需要用户登录")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/{id}/favorite/status")
    public Result<Boolean> checkFavoriteStatus(
            HttpServletRequest request,
            @Parameter(description = "活动ID", required = true, example = "1")
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        boolean isFavorited = favoriteService.isFavorited(userId, id);
        return Result.success(isFavorited);
    }

    @Operation(summary = "模糊搜索活动", description = "根据关键词模糊搜索活动标题、内容、地点、组织者")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "搜索成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @GetMapping("/search")
    public Result<PageResponse<Activity>> searchActivities(
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "搜索关键词", example = "讲座")
            @RequestParam String keyword) {
        PageResponse<Activity> list = activityService.searchActivities(page, size, keyword);
        return Result.success(list);
    }

    @Operation(summary = "筛选活动", description = "按分类和状态筛选活动")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "筛选成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @GetMapping("/filter")
    public Result<PageResponse<Activity>> filterActivities(
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "分类ID", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "活动状态: DRAFT-草稿, PENDING-待审核, PUBLISHED-已发布, REJECTED-已驳回, CANCELLED-已取消, COMPLETED-已完成", example = "PUBLISHED")
            @RequestParam(required = false) String status) {
        Status statusEnum = status != null ? Status.valueOf(status) : null;
        PageResponse<Activity> list = activityService.filterActivities(page, size, categoryId, statusEnum);
        return Result.success(list);
    }

}
