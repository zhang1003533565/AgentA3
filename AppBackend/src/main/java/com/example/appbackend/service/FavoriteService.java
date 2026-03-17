package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;

public interface FavoriteService {

    /**
     * 收藏活动
     */
    void addFavorite(Long userId, Long activityId);

    /**
     * 取消收藏
     */
    void removeFavorite(Long userId, Long activityId);

    /**
     * 检查是否已收藏
     */
    boolean isFavorited(Long userId, Long activityId);

    /**
     * 获取用户的收藏列表
     */
    PageResponse<Activity> getUserFavorites(Long userId, Integer page, Integer size);

    /**
     * 获取活动的收藏数
     */
    long getFavoriteCount(Long activityId);
}
