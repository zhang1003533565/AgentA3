package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forum/posts")
public class PostController {

    @Autowired
    private PostService postService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(request.getAttribute("role"));
    }

    private void checkAdmin(HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "仅管理员可操作");
        }
    }

    @PostMapping
    public Result<PostResponse> createPost(@Valid @RequestBody PostRequest postRequest, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success("发布成功", postService.createPost(postRequest, userId));
    }

    @PutMapping("/{id}")
    public Result<PostResponse> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest postRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success("修改成功", postService.updatePost(id, postRequest, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deletePost(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        if (isAdmin(request)) {
            postService.deletePostByAdmin(id);
        } else {
            postService.deletePost(id, userId);
        }
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDeletePosts(@RequestBody List<Long> ids, HttpServletRequest request) {
        checkAdmin(request);
        postService.batchDeletePostsByAdmin(ids);
        return Result.success("批量删除成功", null);
    }

    @GetMapping("/{id}")
    public Result<PostResponse> getPostDetail(@PathVariable Long id, HttpServletRequest request) {
        return Result.success("操作成功", postService.getPostDetail(id, getCurrentUserId(request)));
    }

    @GetMapping
    public Result<PageResponse<PostListItem>> getPostList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        if (status != null && !"PUBLISHED".equalsIgnoreCase(status) && !isAdmin(request)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "仅管理员可查看非公开帖子");
        }
        PageResponse<PostListItem> page = postService.getPostList(
                pageNum, pageSize, topicId, keyword, sortBy, userId, status, getCurrentUserId(request));
        return Result.success(page);
    }

    @GetMapping("/admin/list")
    public Result<PageResponse<PostListItem>> getAdminPostList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Long topicId,
            HttpServletRequest request) {
        checkAdmin(request);
        PageResponse<PostListItem> page = postService.getAdminPostList(pageNum, pageSize, keyword, status, sortBy, topicId);
        return Result.success(page);
    }

    @GetMapping("/hot")
    public Result<PageResponse<HotPostItem>> getHotPosts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success("操作成功", postService.getHotPosts(pageNum, pageSize));
    }

    @PutMapping("/{id}/pin")
    public Result<Void> togglePin(@PathVariable Long id, HttpServletRequest request) {
        checkAdmin(request);
        postService.togglePin(id);
        return Result.success("操作成功", null);
    }

    @PutMapping("/{id}/highlight")
    public Result<Void> toggleHighlight(@PathVariable Long id, HttpServletRequest request) {
        checkAdmin(request);
        postService.toggleHighlight(id);
        return Result.success("操作成功", null);
    }

    @PutMapping("/{id}/hidden")
    public Result<Void> toggleHidden(@PathVariable Long id, HttpServletRequest request) {
        checkAdmin(request);
        postService.toggleHidden(id);
        return Result.success("操作成功", null);
    }
}
