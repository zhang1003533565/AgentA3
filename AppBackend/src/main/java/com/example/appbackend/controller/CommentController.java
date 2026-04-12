package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CommentService;
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
@RequestMapping("/api/forum/comments")
@Tag(name = "评论管理", description = "评论的发布、删除、查询等接口")
public class CommentController {

    @Autowired
    private CommentService commentService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    private void checkAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "只有管理员可操作");
        }
    }

    @Operation(summary = "发布评论", description = "对帖子发布评论，支持回复已有评论")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "评论成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "帖子不存在"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @PostMapping
    public Result<CommentResponse> createComment(
            @Valid @RequestBody CommentRequest commentRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        CommentResponse comment = commentService.createComment(commentRequest, userId);
        return Result.success("评论成功", comment);
    }

    @Operation(summary = "删除评论", description = "删除指定评论，只有评论作者可以删除")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(
            @Parameter(description = "评论ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        commentService.deleteComment(id, userId);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "管理员删除评论", description = "管理员删除指定评论")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @DeleteMapping("/admin/{id}")
    public Result<Void> adminDeleteComment(
            @Parameter(description = "评论ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkAdmin(request);
        commentService.deleteCommentByAdmin(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "获取帖子评论列表", description = "分页获取指定帖子的评论列表，包含子评论")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @GetMapping
    public Result<PageResponse<CommentResponse>> getCommentList(
            @Parameter(description = "帖子ID", required = true, example = "1")
            @RequestParam Long postId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        PageResponse<CommentResponse> page = commentService.getCommentList(postId, pageNum, pageSize, currentUserId);
        return Result.success("操作成功", page);
    }

    @Operation(summary = "获取评论详情", description = "获取指定评论的详细信息，包含子评论")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @GetMapping("/{id}")
    public Result<CommentResponse> getCommentDetail(
            @Parameter(description = "评论ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        CommentResponse comment = commentService.getCommentDetail(id, currentUserId);
        return Result.success("操作成功", comment);
    }
}
