package com.example.appbackend.repository;

import com.example.appbackend.entity.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {

    /**
     * 根据档口 ID 查询菜品列表
     */
    List<Dish> findByStallId(Long stallId);

    /**
     * 根据档口 ID 和可用状态查询菜品列表
     */
    List<Dish> findByStallIdAndIsAvailable(Long stallId, Boolean isAvailable);

    List<Dish> findByStallPlaceId(Long stallPlaceId);

    /**
     * 根据菜品分类查询
     */
    List<Dish> findByCategory(String category);

    /**
     * 根据口味类型查询
     */
    List<Dish> findByTaste(String taste);

    /**
     * 根据菜品名称模糊查询
     */
    List<Dish> findByNameContaining(String name);

    List<Dish> findByCuisineId(Long cuisineId);

    boolean existsByCuisineId(Long cuisineId);
}
