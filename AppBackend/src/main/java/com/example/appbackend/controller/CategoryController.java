package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "活动分类管理", description = "活动分类接口")
public class CategoryController {

    @Operation(summary = "分类列表", description = "获取所有活动分类")
    @GetMapping
    public Result<Object> getCategoryList() {
        return Result.success();
    }

    @Operation(summary = "创建分类", description = "创建活动分类（管理员/教师）")
    @PostMapping
    public Result<Void> createCategory() {
        return Result.success();
    }

    @Operation(summary = "更新分类", description = "更新活动分类（管理员/教师）")
    @PutMapping("/{id}")
    public Result<Void> updateCategory(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "删除分类", description = "删除活动分类（管理员）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        return Result.success();
    }
}
