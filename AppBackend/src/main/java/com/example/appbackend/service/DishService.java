package com.example.appbackend.service;

import com.example.appbackend.dto.DishDTO;

import java.util.List;

public interface DishService {

    /**
     * 根据档口 ID 获取菜品列表
     */
    List<DishDTO> getDishesByStallId(Long stallId);

    /**
     * 获取所有菜品
     */
    List<DishDTO> getAllDishes();

    /**
     * 根据 ID 获取菜品详情
     */
    DishDTO getDishById(Long id);

    /**
     * 创建菜品
     */
    DishDTO createDish(DishDTO request);

    /**
     * 更新菜品
     */
    DishDTO updateDish(Long id, DishDTO request);

    /**
     * 删除菜品
     */
    void deleteDish(Long id);

    /**
     * 根据分类获取菜品
     */
    List<DishDTO> getDishesByCategory(String category);

    /**
     * 根据口味获取菜品
     */
    List<DishDTO> getDishesByTaste(String taste);

    /**
     * 根据名称搜索菜品
     */
    List<DishDTO> searchDishesByName(String keyword);
}