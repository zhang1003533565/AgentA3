package com.example.appbackend.controller;

import com.example.appbackend.dto.DishReviewDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.DishReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dish-review")
@Tag(name = "菜品评价管理", description = "菜品评价的增删改查接口")
public class DishReviewController {

    @Autowired
    private DishReviewService dishReviewService;

    /**
     * 从请求中获取当前用户 ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof String) {
            try {
                return Long.parseLong((String) userIdObj);
            } catch (NumberFormatException e) {
                throw new BusinessException(401, "用户 ID 格式错误");
            }
        }
        throw new BusinessException(401, "用户 ID 类型错误");
    }

    @GetMapping("/list")
    @Operation(summary = "获取评价列表", description = "根据菜品 ID 或档口 ID 获取评价列表")
    public Result<List<DishReviewDTO>> getReviewList(
            @Parameter(description = "菜品 ID（可选）")
            @RequestParam(required = false) Long dishId,
            @Parameter(description = "档口 ID（可选）")
            @RequestParam(required = false) Long stallId) {

        List<DishReviewDTO> result;
        if (dishId != null) {
            result = dishReviewService.getReviewsByDishId(dishId);
        } else if (stallId != null) {
            result = dishReviewService.getReviewsByStallId(stallId);
        } else {
            throw new BusinessException(400, "请提供菜品 ID 或档口 ID");
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取评价详情", description = "根据评价 ID 获取详细信息")
    public Result<DishReviewDTO> getReviewDetail(
            @PathVariable Long id) {
        DishReviewDTO result = dishReviewService.getReviewById(id);
        return Result.success(result);
    }

    @PostMapping
    @Operation(summary = "创建评价", description = "对菜品进行评价")
    public Result<DishReviewDTO> createReview(
            @Valid @RequestBody DishReviewDTO request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        DishReviewDTO result = dishReviewService.createReview(userId, request);
        return Result.success("评价创建成功", result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新评价", description = "更新已创建的评价")
    public Result<DishReviewDTO> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody DishReviewDTO request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        DishReviewDTO result = dishReviewService.updateReview(id, userId, request);
        return Result.success("评价更新成功", result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除评价", description = "删除已创建的评价")
    public Result<Void> deleteReview(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        dishReviewService.deleteReview(id, userId);
        return Result.success("评价删除成功", null);
    }

    @GetMapping("/count")
    @Operation(summary = "获取评价数量", description = "统计菜品或档口的评价数量")
    public Result<Integer> getReviewCount(
            @Parameter(description = "菜品 ID")
            @RequestParam(required = false) Long dishId,
            @Parameter(description = "档口 ID")
            @RequestParam(required = false) Long stallId) {

        int count;
        if (dishId != null) {
            count = dishReviewService.countByDishId(dishId);
        } else if (stallId != null) {
            count = dishReviewService.countByStallId(stallId);
        } else {
            throw new BusinessException(400, "请提供菜品 ID 或档口 ID");
        }
        return Result.success(count);
    }
}