package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/users")
@Tag(name = "用户中心", description = "用户帖子、点赞等接口")
public class UserCenterController {

    @Operation(summary = "获取用户帖子列表", description = "获取指定用户的帖子列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/{userId}/posts")
    public Result<Void> getUserPosts(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20") 
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success("操作成功", null);
    }

    @Operation(summary = "获取用户点赞列表", description = "获取指定用户点赞的帖子列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/{userId}/likes")
    public Result<Void> getUserLikes(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20") 
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success("操作成功", null);
    }
}
