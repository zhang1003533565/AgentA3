package com.example.appbackend.controller;

import com.example.appbackend.dto.FacilityCategoryRequest;
import com.example.appbackend.dto.FacilityFloorRequest;
import com.example.appbackend.entity.FacilityFloor;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.StallCuisine;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.DiningCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/facility")
@Tag(name = "设施楼层与档口菜系", description = "维护校园设施通用楼层与食堂档口菜系")
public class FacilityDiningCategoryController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final DiningCategoryService categoryService;

    public FacilityDiningCategoryController(DiningCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/floors")
    @Operation(summary = "获取校园设施楼层列表")
    public Result<List<FacilityFloor>> listFloors(@RequestParam Long facilityId) {
        return Result.success(categoryService.listFloors(facilityId));
    }

    @PostMapping("/floors")
    @Operation(summary = "新增校园设施楼层")
    public Result<FacilityFloor> createFloor(
            @Valid @RequestBody FacilityFloorRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        return Result.success("楼层已新增", categoryService.createFloor(request));
    }

    @PutMapping("/floors/{id}")
    @Operation(summary = "编辑校园设施楼层")
    public Result<FacilityFloor> updateFloor(
            @PathVariable Long id,
            @Valid @RequestBody FacilityFloorRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        return Result.success("楼层已更新", categoryService.updateFloor(id, request));
    }

    @DeleteMapping("/floors/{id}")
    @Operation(summary = "删除校园设施楼层")
    public Result<Void> deleteFloor(@PathVariable Long id, HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        categoryService.deleteFloor(id);
        return Result.success("楼层已删除", null);
    }

    @GetMapping("/stall-cuisines")
    @Operation(summary = "获取档口菜系列表")
    public Result<List<StallCuisine>> listCuisines(@RequestParam Long restaurantId) {
        return Result.success(categoryService.listCuisines(restaurantId));
    }

    @PostMapping("/stall-cuisines")
    @Operation(summary = "新增档口菜系")
    public Result<StallCuisine> createCuisine(
            @Valid @RequestBody FacilityCategoryRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        return Result.success("菜系已新增", categoryService.createCuisine(request));
    }

    @PutMapping("/stall-cuisines/{id}")
    @Operation(summary = "编辑档口菜系")
    public Result<StallCuisine> updateCuisine(
            @PathVariable Long id,
            @Valid @RequestBody FacilityCategoryRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        return Result.success("菜系已更新", categoryService.updateCuisine(id, request));
    }

    @DeleteMapping("/stall-cuisines/{id}")
    @Operation(summary = "删除档口菜系")
    public Result<Void> deleteCuisine(@PathVariable Long id, HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        categoryService.deleteCuisine(id);
        return Result.success("菜系已删除", null);
    }

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!ROLE_ADMIN.equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作");
        }
    }
}
