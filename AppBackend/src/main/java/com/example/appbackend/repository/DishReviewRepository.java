package com.example.appbackend.repository;

import com.example.appbackend.entity.DishReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DishReviewRepository extends JpaRepository<DishReview, Long> {

    /**
     * 根据菜品 ID 查询评价列表
     */
    List<DishReview> findByDishIdAndStatus(Long dishId, Integer status);

    /**
     * 根据档口 ID 查询评价列表
     */
    List<DishReview> findByStallIdAndStatus(Long stallId, Integer status);

    /**
     * 根据用户 ID 查询评价列表
     */
    List<DishReview> findByUserIdAndStatus(Long userId, Integer status);

    /**
     * 根据菜品 ID 和用户 ID 查询评价
     */
    DishReview findByDishIdAndUserId(Long dishId, Long userId);

    /**
     * 统计菜品的评价数量
     */
    int countByDishIdAndStatus(Long dishId, Integer status);

    /**
     * 统计档口的评价数量
     */
    int countByStallIdAndStatus(Long stallId, Integer status);
}