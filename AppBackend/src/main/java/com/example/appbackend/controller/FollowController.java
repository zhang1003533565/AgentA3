package com.example.appbackend.controller;

import com.example.appbackend.dto.FollowListItem;
import com.example.appbackend.dto.FollowRequest;
import com.example.appbackend.dto.FollowStatusResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forum/follows")
@Tag(name = "关注管理", description = "关注用户、取消关注、获取粉丝和关注列表")
public class FollowController {

    @Autowired
    private FollowService followService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        if (userId instanceof String && !((String) userId).isBlank()) {
            return Long.parseLong((String) userId);
        }
        return null;
    }

    @Operation(summary = "关注或取消关注用户")
    @PostMapping
    public Result<FollowStatusResponse> followUser(
            @Valid @RequestBody FollowRequest followRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        boolean followed = followService.toggleFollow(followRequest, userId);
        FollowStatusResponse status = followService.getFollowStatus(followRequest.getFollowId(), userId);
        return Result.success(followed ? "关注成功" : "取消关注成功", status);
    }

    @Operation(summary = "获取我的粉丝列表")
    @GetMapping("/my/followers")
    public Result<PageResponse<FollowListItem>> getMyFollowers(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }
        PageResponse<FollowListItem> page = followService.getFollowers(currentUserId, currentUserId, pageNum, pageSize);
        return Result.success("操作成功", page);
    }

    @Operation(summary = "获取我的关注列表")
    @GetMapping("/my/following")
    public Result<PageResponse<FollowListItem>> getMyFollowing(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        if (currentUserId == null) {
            return Result.unauthorized("请先登录");
        }
        PageResponse<FollowListItem> page = followService.getFollowing(currentUserId, currentUserId, pageNum, pageSize);
        return Result.success("操作成功", page);
    }

    @Operation(summary = "获取用户粉丝列表")
    @GetMapping("/followers/{userId}")
    public Result<PageResponse<FollowListItem>> getFollowers(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        PageResponse<FollowListItem> page = followService.getFollowers(userId, currentUserId, pageNum, pageSize);
        return Result.success("操作成功", page);
    }

    @Operation(summary = "获取用户关注列表")
    @GetMapping("/following/{userId}")
    public Result<PageResponse<FollowListItem>> getFollowing(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        PageResponse<FollowListItem> page = followService.getFollowing(userId, currentUserId, pageNum, pageSize);
        return Result.success("操作成功", page);
    }

    @Operation(summary = "获取关注状态")
    @GetMapping("/status/{userId}")
    public Result<FollowStatusResponse> getFollowStatus(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        FollowStatusResponse status = followService.getFollowStatus(userId, currentUserId);
        return Result.success("操作成功", status);
    }
}
