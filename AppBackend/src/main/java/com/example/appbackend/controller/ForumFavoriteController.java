package com.example.appbackend.controller;

import com.example.appbackend.dto.FavoriteStatusResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.PostListItem;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.ForumFavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/favorites")
public class ForumFavoriteController {

    @Autowired
    private ForumFavoriteService favoriteService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    @PostMapping("/{postId}")
    public Result<FavoriteStatusResponse> toggleFavorite(@PathVariable Long postId, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        FavoriteStatusResponse response = favoriteService.toggleFavorite(postId, userId);
        return Result.success(Boolean.TRUE.equals(response.getFavorited()) ? "收藏成功" : "取消收藏成功", response);
    }

    @GetMapping("/status/{postId}")
    public Result<FavoriteStatusResponse> getFavoriteStatus(@PathVariable Long postId, HttpServletRequest request) {
        FavoriteStatusResponse response = favoriteService.getFavoriteStatus(postId, getCurrentUserId(request));
        return Result.success("操作成功", response);
    }

    @GetMapping("/my")
    public Result<PageResponse<PostListItem>> getMyFavorites(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success("操作成功", favoriteService.getMyFavorites(userId, pageNum, pageSize));
    }
}
