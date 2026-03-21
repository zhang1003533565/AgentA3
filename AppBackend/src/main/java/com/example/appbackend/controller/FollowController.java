package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/follows")
@Tag(name = "关注管理", description = "关注用户、取消关注、获取粉丝/关注列表等接口")
public class FollowController {

    @Autowired
    private FollowService followService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    @Operation(summary = "关注用户", description = "关注指定用户，若已关注则取消关注")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PostMapping
    public Result<Void> followUser(
            @Valid @RequestBody FollowRequest followRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        boolean followed = followService.toggleFollow(followRequest, userId);
        return Result.success(followed ? "关注成功" : "取消关注成功", null);
    }

    @Operation(summary = "获取用户粉丝列表", description = "获取指定用户的粉丝列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
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

    @Operation(summary = "获取用户关注列表", description = "获取指定用户的关注列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
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

    @Operation(summary = "获取关注状态", description = "获取当前用户对指定用户的关注状态")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
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
