package com.example.appbackend.controller;

import com.example.appbackend.dto.CommentRequest;
import com.example.appbackend.dto.CommentResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    private void checkAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "只有管理员可操作");
        }
    }

    @PostMapping
    public Result<CommentResponse> createComment(
            @Valid @RequestBody CommentRequest commentRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success("评论成功", commentService.createComment(commentRequest, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        commentService.deleteComment(id, userId);
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/admin/{id}")
    public Result<Void> adminDeleteComment(@PathVariable Long id, HttpServletRequest request) {
        if (getCurrentUserId(request) == null) {
            return Result.unauthorized("请先登录");
        }
        checkAdmin(request);
        commentService.deleteCommentByAdmin(id);
        return Result.success("删除成功", null);
    }

    @GetMapping
    public Result<PageResponse<CommentResponse>> getCommentList(
            @RequestParam Long postId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        PageResponse<CommentResponse> page = commentService.getCommentList(
                postId, pageNum, pageSize, getCurrentUserId(request));
        return Result.success("操作成功", page);
    }

    @GetMapping("/admin/list")
    public Result<PageResponse<CommentResponse>> getAdminCommentList(
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        if (getCurrentUserId(request) == null) {
            return Result.unauthorized("请先登录");
        }
        checkAdmin(request);
        PageResponse<CommentResponse> page = commentService.getAdminCommentList(postId, keyword, status, pageNum, pageSize);
        return Result.success("操作成功", page);
    }

    @GetMapping("/{id}")
    public Result<CommentResponse> getCommentDetail(@PathVariable Long id, HttpServletRequest request) {
        return Result.success("操作成功", commentService.getCommentDetail(id, getCurrentUserId(request)));
    }
}
