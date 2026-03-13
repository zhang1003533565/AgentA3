package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
@Tag(name = "评论管理", description = "评论接口")
public class CommentController {

    @Operation(summary = "活动评论列表", description = "获取活动的评论列表")
    @GetMapping("/activities/{activityId}/comments")
    public Result<Object> getComments(@PathVariable Long activityId) {
        return Result.success();
    }

    @Operation(summary = "发表评论", description = "为活动发表评论")
    @PostMapping("/activities/{activityId}/comments")
    public Result<Void> addComment(@PathVariable Long activityId) {
        return Result.success();
    }

    @Operation(summary = "删除评论", description = "删除评论")
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        return Result.success();
    }
}
