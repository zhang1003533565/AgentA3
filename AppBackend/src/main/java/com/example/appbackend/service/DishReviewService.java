package com.example.appbackend.service;

import com.example.appbackend.dto.DishReviewDTO;

import java.util.List;

public interface DishReviewService {

    /**
     * 根据菜品 ID 获取评价列表
     */
    List<DishReviewDTO> getReviewsByDishId(Long dishId);

    /**
     * 根据档口 ID 获取评价列表
     */
    List<DishReviewDTO> getReviewsByStallId(Long stallId);

    /**
     * 获取评价详情
     */
    DishReviewDTO getReviewById(Long id);

    /**
     * 创建评价
     */
    DishReviewDTO createReview(Long userId, DishReviewDTO request);

    /**
     * 更新评价
     */
    DishReviewDTO updateReview(Long id, Long userId, DishReviewDTO request);

    /**
     * 删除评价
     */
    void deleteReview(Long id, Long userId);

    /**
     * 统计菜品的评价数量
     */
    int countByDishId(Long dishId);

    /**
     * 统计档口的评价数量
     */
    int countByStallId(Long stallId);
}