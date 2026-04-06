package com.example.appbackend.controller;

import com.example.appbackend.dto.CanteenStallDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CanteenStallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/canteen-stall")
@Tag(name = "食堂档口管理", description = "食堂档口的增删改查接口")
public class CanteenStallController {

    @Autowired
    private CanteenStallService canteenStallService;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(403, "无权限操作");
        }
    }

    @GetMapping("/list")
    @Operation(summary = "获取档口列表", description = "根据餐厅 ID 获取档口列表，或获取所有档口")
    public Result<List<CanteenStallDTO>> getStallList(
            @Parameter(description = "餐厅 ID（可选，不传则返回所有档口）")
            @RequestParam(required = false) Long restaurantId,
            @Parameter(description = "品类（可选）")
            @RequestParam(required = false) String category,
            @Parameter(description = "楼层（可选）")
            @RequestParam(required = false) String floor) {

        List<CanteenStallDTO> result;
        if (restaurantId != null) {
            result = canteenStallService.getStallListByRestaurantId(restaurantId);
        } else if (category != null) {
            result = canteenStallService.getStallsByCategory(category);
        } else if (floor != null) {
            result = canteenStallService.getStallsByFloor(floor);
        } else {
            result = canteenStallService.getAllStalls();
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取档口详情", description = "根据档口 ID 获取详细信息")
    public Result<CanteenStallDTO> getStallDetail(
            @PathVariable Long id) {
        CanteenStallDTO result = canteenStallService.getStallById(id);
        return Result.success(result);
    }

    @PostMapping
    @Operation(summary = "新增档口", description = "创建新的食堂档口")
    public Result<CanteenStallDTO> createStall(
            @Valid @RequestBody CanteenStallDTO request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        CanteenStallDTO result = canteenStallService.createStall(request);
        return Result.success("档口创建成功", result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新档口", description = "更新指定档口的信息")
    public Result<CanteenStallDTO> updateStall(
            @PathVariable Long id,
            @Valid @RequestBody CanteenStallDTO request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        CanteenStallDTO result = canteenStallService.updateStall(id, request);
        return Result.success("档口更新成功", result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除档口", description = "删除指定的食堂档口")
    public Result<Void> deleteStall(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        canteenStallService.deleteStall(id);
        return Result.success("档口删除成功", null);
    }
}