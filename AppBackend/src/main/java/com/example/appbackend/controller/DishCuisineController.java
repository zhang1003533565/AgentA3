package com.example.appbackend.controller;

import com.example.appbackend.dto.DishCuisineRequest;
import com.example.appbackend.entity.DishCuisine;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.DishCuisineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dish-cuisines")
public class DishCuisineController {

    private final DishCuisineService cuisineService;

    public DishCuisineController(DishCuisineService cuisineService) {
        this.cuisineService = cuisineService;
    }

    @GetMapping
    public Result<List<DishCuisine>> list(@RequestParam Long canteenPlaceId) {
        return Result.success(cuisineService.list(canteenPlaceId));
    }

    @PostMapping
    public Result<DishCuisine> create(
            @Valid @RequestBody DishCuisineRequest request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        return Result.success("菜系已新增", cuisineService.create(request));
    }

    @PutMapping("/{id}")
    public Result<DishCuisine> update(
            @PathVariable Long id,
            @Valid @RequestBody DishCuisineRequest request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        return Result.success("菜系已更新", cuisineService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        cuisineService.delete(id);
        return Result.success("菜系已删除", null);
    }

    private void checkAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作");
        }
    }
}
