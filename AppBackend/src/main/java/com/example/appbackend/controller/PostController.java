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
@RequestMapping("/api/forum/posts")
@Tag(name = "帖子管理", description = "帖子的发布、编辑、删除、查询等接口")
public class PostController {

    @Operation(summary = "发布帖子", description = "发布一个新帖子")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "发布成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @PostMapping
    public Result<Void> createPost(@RequestBody String postJson, HttpServletRequest request) {
        return Result.success("发布成功", null);
    }

    @Operation(summary = "编辑帖子", description = "编辑指定帖子")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "修改成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @PutMapping("/{id}")
    public Result<Void> updatePost(
            @Parameter(description = "帖子ID", required = true, example = "1") 
            @PathVariable Long id,
            @RequestBody String postJson, 
            HttpServletRequest request) {
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除帖子", description = "删除指定帖子")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(
            @Parameter(description = "帖子ID", required = true, example = "1") 
            @PathVariable Long id, 
            HttpServletRequest request) {
        return Result.success("删除成功", null);
    }

    @Operation(summary = "获取帖子详情", description = "获取指定帖子的详细信息")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @GetMapping("/{id}")
    public Result<Void> getPostDetail(
            @Parameter(description = "帖子ID", required = true, example = "1") 
            @PathVariable Long id) {
        return Result.success("操作成功", null);
    }

    @Operation(summary = "获取帖子列表", description = "分页获取帖子列表，支持筛选和搜索")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping
    public Result<Void> getPostList(
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "10") 
            @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "话题ID") 
            @RequestParam(required = false) Long topicId,
            @Parameter(description = "关键词搜索") 
            @RequestParam(required = false) String keyword,
            @Parameter(description = "排序方式") 
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "用户ID") 
            @RequestParam(required = false) Long userId) {
        return Result.success("操作成功", null);
    }

    @Operation(summary = "获取热门帖子", description = "获取热门帖子列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/hot")
    public Result<Void> getHotPosts(
            @Parameter(description = "返回数量", example = "10") 
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success("操作成功", null);
    }
}
