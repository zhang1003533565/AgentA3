package com.example.appbackend.controller;

import com.example.appbackend.dto.FavoriteItem;
import com.example.appbackend.dto.FavoriteRequest;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.MapFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/map/favorite")
@Tag(name = "收藏管理", description = "收藏目的地的添加、查询、删除接口")
public class FavoriteController {

    @Autowired
    private MapFavoriteService favoriteService;

    @PostMapping
    @Operation(summary = "添加收藏目的地", description = "收藏指定标记的目的地")
    public Result<FavoriteItem> addFavorite(
            @Valid @RequestBody FavoriteRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        FavoriteItem result = favoriteService.addFavorite(request, userId);
        return Result.success("收藏成功", result);
    }

    @GetMapping("/list")
    @Operation(summary = "获取收藏列表", description = "获取用户收藏的目的地列表")
    public Result<List<FavoriteItem>> getFavoriteList(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        List<FavoriteItem> result = favoriteService.getFavoriteList(userId);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除收藏", description = "取消收藏指定的目的地")
    public Result<Void> deleteFavorite(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        favoriteService.deleteFavorite(id, userId);
        return Result.success("取消收藏成功", null);
    }
}
