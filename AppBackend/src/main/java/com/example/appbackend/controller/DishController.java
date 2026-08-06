package com.example.appbackend.controller;

import com.example.appbackend.dto.DishDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dish")
@Tag(name = "菜品管理", description = "菜品的增删改查接口")
public class DishController {

    @Autowired
    private DishService dishService;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(403, "无权限操作");
        }
    }

    @GetMapping("/list")
    @Operation(summary = "获取菜品列表", description = "根据档口 ID、分类或名称获取菜品列表")
    public Result<List<DishDTO>> getDishList(
            @Parameter(description = "档口 ID（可选）")
            @RequestParam(required = false) Long stallId,
            @Parameter(description = "档口点位 ID（可选）")
            @RequestParam(required = false) Long stallPlaceId,
            @Parameter(description = "分类（可选）")
            @RequestParam(required = false) String category,
            @Parameter(description = "口味（可选）")
            @RequestParam(required = false) String taste,
            @Parameter(description = "名称关键词（可选）")
            @RequestParam(required = false) String name) {

        List<DishDTO> result;
        if (stallPlaceId != null) {
            result = dishService.getDishesByStallPlaceId(stallPlaceId);
        } else if (stallId != null) {
            result = dishService.getDishesByStallId(stallId);
        } else if (category != null) {
            result = dishService.getDishesByCategory(category);
        } else if (taste != null) {
            result = dishService.getDishesByTaste(taste);
        } else if (name != null) {
            result = dishService.searchDishesByName(name);
        } else {
            result = dishService.getAllDishes();
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取菜品详情", description = "根据菜品 ID 获取详细信息")
    public Result<DishDTO> getDishDetail(
            @PathVariable Long id) {
        DishDTO result = dishService.getDishById(id);
        return Result.success(result);
    }

    @PostMapping
    @Operation(summary = "新增菜品", description = "创建新的菜品")
    public Result<DishDTO> createDish(
            @Valid @RequestBody DishDTO request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        DishDTO result = dishService.createDish(request);
        return Result.success("菜品创建成功", result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新菜品", description = "更新指定菜品的信息")
    public Result<DishDTO> updateDish(
            @PathVariable Long id,
            @Valid @RequestBody DishDTO request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        DishDTO result = dishService.updateDish(id, request);
        return Result.success("菜品更新成功", result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜品", description = "删除指定的菜品")
    public Result<Void> deleteDish(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        dishService.deleteDish(id);
        return Result.success("菜品删除成功", null);
    }
}
