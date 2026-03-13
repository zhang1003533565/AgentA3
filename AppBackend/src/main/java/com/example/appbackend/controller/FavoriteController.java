package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
@Tag(name = "收藏管理", description = "收藏接口")
public class FavoriteController {

    @Operation(summary = "收藏活动", description = "收藏活动（学生）")
    @PostMapping
    public Result<Void> addFavorite() {
        return Result.success();
    }

    @Operation(summary = "取消收藏", description = "取消收藏活动")
    @DeleteMapping("/{activityId}")
    public Result<Void> removeFavorite(@PathVariable Long activityId) {
        return Result.success();
    }

    @Operation(summary = "我的收藏列表", description = "获取当前用户的收藏列表")
    @GetMapping("/my-favorites")
    public Result<Object> getMyFavorites() {
        return Result.success();
    }

    @Operation(summary = "检查是否收藏", description = "检查活动是否被当前用户收藏")
    @GetMapping("/activities/{activityId}/favorite-status")
    public Result<Object> getFavoriteStatus(@PathVariable Long activityId) {
        return Result.success();
    }
}
