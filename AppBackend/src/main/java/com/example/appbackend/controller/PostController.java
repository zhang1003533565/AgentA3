package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.PostService;
import com.example.appbackend.util.JwtUtil;
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
@RequestMapping("/api/forum/posts")
@Tag(name = "帖子管理", description = "帖子的发布、编辑、删除、查询等接口")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getUserIdFromToken(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Operation(summary = "发布帖子", description = "发布一个新帖子")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "发布成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @PostMapping
    public Result<PostResponse> createPost(
            @Valid @RequestBody PostRequest postRequest,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        PostResponse post = postService.createPost(postRequest, userId);
        return Result.success("发布成功", post);
    }

    @Operation(summary = "编辑帖子", description = "编辑指定帖子")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "修改成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @PutMapping("/{id}")
    public Result<PostResponse> updatePost(
            @Parameter(description = "帖子ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody PostRequest postRequest,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        PostResponse post = postService.updatePost(id, postRequest, userId);
        return Result.success("修改成功", post);
    }

    @Operation(summary = "删除帖子", description = "删除指定帖子")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(
            @Parameter(description = "帖子ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        postService.deletePost(id, userId);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "获取帖子详情", description = "获取指定帖子的详细信息")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @GetMapping("/{id}")
    public Result<PostResponse> getPostDetail(
            @Parameter(description = "帖子ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromToken(request);
        PostResponse post = postService.getPostDetail(id, currentUserId);
        return Result.success("操作成功", post);
    }

    @Operation(summary = "获取帖子列表", description = "分页获取帖子列表，支持筛选和搜索")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功")
    })
    @GetMapping
    public Result<PageResponse<PostListItem>> getPostList(
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
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromToken(request);
        PageResponse<PostListItem> page = postService.getPostList(pageNum, pageSize, topicId, keyword, sortBy, userId, currentUserId);
        return Result.success(page);
    }

    @Operation(summary = "获取热门帖子", description = "获取热门帖子列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功")
    })
    @GetMapping("/hot")
    public Result<PageResponse<HotPostItem>> getHotPosts(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResponse<HotPostItem> hotPosts = postService.getHotPosts(pageNum, pageSize);
        return Result.success("操作成功", hotPosts);
    }
}
