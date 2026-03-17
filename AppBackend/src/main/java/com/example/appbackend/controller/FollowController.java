package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/follows")
@Tag(name = "关注管理", description = "关注用户、取消关注、获取粉丝/关注列表等接口")
public class FollowController {

    @Operation(summary = "关注用户", description = "关注指定用户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "关注成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @PostMapping
    public Result<Void> followUser(@RequestBody String followJson, HttpServletRequest request) {
        return Result.success("关注成功", null);
    }

    @Operation(summary = "取消关注", description = "取消关注指定用户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "取消关注成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @DeleteMapping("/{followId}")
    public Result<Void> unfollowUser(
            @Parameter(description = "被关注用户ID", required = true, example = "1") 
            @PathVariable Long followId, 
            HttpServletRequest request) {
        return Result.success("取消关注成功", null);
    }

    @Operation(summary = "获取用户粉丝列表", description = "获取指定用户的粉丝列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/followers/{userId}")
    public Result<Void> getFollowers(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20") 
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success("操作成功", null);
    }

    @Operation(summary = "获取用户关注列表", description = "获取指定用户的关注列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/following/{userId}")
    public Result<Void> getFollowing(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20") 
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success("操作成功", null);
    }

    @Operation(summary = "获取关注状态", description = "获取当前用户对指定用户的关注状态")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/status/{userId}")
    public Result<Void> getFollowStatus(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long userId) {
        return Result.success("操作成功", null);
    }
}
