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
@RequestMapping("/api/forum/likes")
@Tag(name = "点赞管理", description = "点赞/取消点赞、获取点赞状态等接口")
public class LikeController {

    @Operation(summary = "点赞/取消点赞", description = "对帖子或评论进行点赞或取消点赞")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @PostMapping
    public Result<Void> toggleLike(@RequestBody String likeJson, HttpServletRequest request) {
        return Result.success("点赞成功", null);
    }

    @Operation(summary = "获取点赞状态", description = "获取当前用户对指定目标的点赞状态")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/status")
    public Result<Void> getLikeStatus(
            @Parameter(description = "目标ID", required = true, example = "1") 
            @RequestParam Long targetId,
            @Parameter(description = "目标类型", required = true, example = "1") 
            @RequestParam Integer targetType) {
        return Result.success("操作成功", null);
    }
}
