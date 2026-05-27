package com.example.appbackend.controller;

import com.example.appbackend.dto.LikeRequest;
import com.example.appbackend.dto.LikeStatusResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.LikeService;
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
@RequestMapping("/api/forum/likes")
@Tag(name = "点赞管理", description = "点赞/取消点赞、获取点赞状态等接口")
public class LikeController {

    @Autowired
    private LikeService likeService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    @Operation(summary = "点赞/取消点赞", description = "对帖子进行点赞或取消点赞，已点赞则取消，未点赞则点赞")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @PostMapping
    public Result<LikeStatusResponse> toggleLike(
            @Valid @RequestBody LikeRequest likeRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        LikeStatusResponse response = likeService.toggleLike(likeRequest, userId);
        boolean liked = Boolean.TRUE.equals(response.getLiked());
        String message = liked ? "点赞成功" : "取消点赞成功";
        return Result.success(message, response);
    }

    @Operation(summary = "获取点赞状态", description = "获取当前用户对指定帖子的点赞状态及总点赞数")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @GetMapping("/status")
    public Result<LikeStatusResponse> getLikeStatus(
            @Parameter(description = "点赞目标ID", required = true, example = "1")
            @RequestParam Long targetId,
            @Parameter(description = "点赞目标类型：POST-帖子，COMMENT-评论", required = true, example = "POST")
            @RequestParam String targetType,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        LikeStatusResponse response = likeService.getLikeStatus(targetId, targetType, currentUserId);
        return Result.success("操作成功", response);
    }
}
