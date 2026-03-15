package com.example.appbackend.controller;

import com.example.appbackend.dto.CategoryRequest;
import com.example.appbackend.dto.CategoryResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ActivitiyCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "活动分类管理", description = "活动分类接口")
public class CategoryController {

    @Autowired
    private ActivitiyCategoryService activitiyCategoryService;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER="TEACHER";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        // 允许 ADMIN 或 TEACHER，拒绝其他（含 null）
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员、教师可执行");
        }
    }


    @Operation(summary = "分类列表", description = "获取所有活动分类")
    @GetMapping
    public Result<List<CategoryResponse>> getCategoryList() {
        List<CategoryResponse> categories = activitiyCategoryService.getAllCategories();
        return Result.success(categories);
    }

    @Operation(summary = "创建分类", description = "创建活动分类（管理员/教师）")
    @PostMapping
    public Result<CategoryResponse> createCategory(HttpServletRequest request, @Valid @RequestBody CategoryRequest categoryRequest) {
            checkAdminRole(request);
            CategoryResponse categoryResponse=activitiyCategoryService.addCategory(categoryRequest);

        return Result.success(categoryResponse);
    }

    @Operation(summary = "更新分类", description = "更新活动分类（管理员/教师）")
    @PutMapping("/{id}")
    public Result<Void> updateCategory(HttpServletRequest request, @PathVariable Long id,@RequestBody CategoryRequest categoryRequest) {
        checkAdminRole(request);
        activitiyCategoryService.update(id,categoryRequest);
        return Result.success();
    }

    @Operation(summary = "删除分类", description = "删除活动分类（管理员）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(HttpServletRequest request, @PathVariable Long id) {
        activitiyCategoryService.delete(id);
        return Result.success();
    }
}
