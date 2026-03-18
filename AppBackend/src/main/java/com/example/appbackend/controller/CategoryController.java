package com.example.appbackend.controller;

import com.example.appbackend.dto.CategoryRequest;
import com.example.appbackend.dto.CategoryResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ActivitiyCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "活动分类管理", description = "活动分类的增删改查接口")
public class CategoryController {

    @Autowired
    private ActivitiyCategoryService activitiyCategoryService;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER="TEACHER";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员、教师可执行");
        }
    }


    @Operation(summary = "获取分类列表", description = "获取所有活动分类列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功")
    })
    @GetMapping
    public Result<List<CategoryResponse>> getCategoryList() {
        List<CategoryResponse> categories = activitiyCategoryService.getAllCategories();
        return Result.success(categories);
    }

    @Operation(summary = "创建分类", description = "创建新的活动分类，需要管理员或教师权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "400", description = "分类名称已存在")
    })
    @PostMapping
    public Result<CategoryResponse> createCategory(
            HttpServletRequest request, 
            @Parameter(description = "分类信息", required = true) 
            @Valid @RequestBody CategoryRequest categoryRequest) {
        checkAdminRole(request);
        CategoryResponse categoryResponse=activitiyCategoryService.addCategory(categoryRequest);
        return Result.success(categoryResponse);
    }

    @Operation(summary = "更新分类", description = "更新活动分类信息，需要管理员或教师权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "分类不存在")
    })
    @PutMapping("/{id}")
    public Result<Void> updateCategory(
            HttpServletRequest request, 
            @Parameter(description = "分类ID", required = true, example = "1") 
            @PathVariable Long id,
            @Parameter(description = "分类信息", required = true) 
            @RequestBody CategoryRequest categoryRequest) {
        checkAdminRole(request);
        activitiyCategoryService.update(id,categoryRequest);
        return Result.success();
    }

    @Operation(summary = "删除分类", description = "删除活动分类，需要管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "分类不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(
            HttpServletRequest request, 
            @Parameter(description = "分类ID", required = true, example = "1") 
            @PathVariable Long id) {
        activitiyCategoryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除分类", description = "批量删除活动分类，需要管理员权限")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @DeleteMapping("/batch")
    public Result<Void> deleteCategories(
            HttpServletRequest request,
            @Parameter(description = "分类ID列表", required = true)
            @RequestBody List<Long> ids) {
        activitiyCategoryService.deleteCategories(ids);
        return Result.success();
    }
}
