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
@RequestMapping("/api/forum/comments")
@Tag(name = "评论管理", description = "评论的发布、删除、查询等接口")
public class CommentController {

    @Operation(summary = "发布评论", description = "对帖子发布评论")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "评论成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @PostMapping
    public Result<Void> createComment(@RequestBody String commentJson, HttpServletRequest request) {
        return Result.success("评论成功", null);
    }

    @Operation(summary = "删除评论", description = "删除指定评论")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(
            @Parameter(description = "评论ID", required = true, example = "1") 
            @PathVariable Long id, 
            HttpServletRequest request) {
        return Result.success("删除成功", null);
    }

    @Operation(summary = "获取帖子评论列表", description = "分页获取指定帖子的评论列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping
    public Result<Void> getCommentList(
            @Parameter(description = "帖子ID", required = true, example = "1") 
            @RequestParam Long postId,
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20") 
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success("操作成功", null);
    }
}
