package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/merchant")
@Tag(name = "商家管理", description = "商家和商家分类接口")
public class MerchantController {

    @Autowired private MerchantService merchantService;

    private static final String ROLE_ADMIN = "ADMIN";

    private boolean isAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        return ROLE_ADMIN.equals(role);
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) userId;
    }

    // ========== 商家分类 ==========

    @GetMapping("/category/list")
    @Operation(summary = "商家分类列表", description = "公开接口")
    public Result<List<MerchantDTO.CategoryVO>> listCategories() {
        return Result.success(merchantService.listCategories());
    }

    @PostMapping("/category")
    @Operation(summary = "新增商家分类", description = "管理员权限")
    public Result<MerchantDTO.CategoryVO> createCategory(
            @Valid @RequestBody MerchantDTO.CategoryRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success("创建成功", merchantService.createCategory(req));
    }

    @PutMapping("/category/{id}")
    @Operation(summary = "编辑商家分类", description = "管理员权限")
    public Result<Void> updateCategory(
            @PathVariable Long id,
            @RequestBody MerchantDTO.CategoryRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        merchantService.updateCategory(id, req);
        return Result.success("更新成功", (Void) null);
    }

    @DeleteMapping("/category/{id}")
    @Operation(summary = "删除商家分类", description = "管理员权限，检查分类下是否有商家")
    public Result<Void> deleteCategory(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        merchantService.deleteCategory(id);
        return Result.success("删除成功", (Void) null);
    }

    // ========== 商家 ==========

    @GetMapping("/list")
    @Operation(summary = "商家列表（分页筛选）", description = "公开接口")
    public Result<PageResponse<MerchantDTO.MerchantVO>> getMerchantList(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "分类ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "关键词")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1-正常营业 2-暂停营业 3-已禁用")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "用户纬度")
            @RequestParam(required = false) java.math.BigDecimal lat,
            @Parameter(description = "用户经度")
            @RequestParam(required = false) java.math.BigDecimal lng,
            @Parameter(description = "排序：distance/latest")
            @RequestParam(required = false) String sort) {
        return Result.success(merchantService.getMerchantList(current, size, categoryId, keyword, status, lat, lng, sort));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商家详情", description = "公开接口，返回商家信息及有效活动列表")
    public Result<MerchantDTO.MerchantDetailVO> getMerchantDetail(@PathVariable Long id) {
        return Result.success(merchantService.getMerchantDetail(id));
    }

    @PostMapping
    @Operation(summary = "新增商家", description = "管理员权限")
    public Result<MerchantDTO.MerchantVO> createMerchant(
            @Valid @RequestBody MerchantDTO.MerchantRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success("创建成功", merchantService.createMerchant(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑商家", description = "管理员权限")
    public Result<Void> updateMerchant(
            @PathVariable Long id,
            @RequestBody MerchantDTO.MerchantRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        merchantService.updateMerchant(id, req);
        return Result.success("更新成功", (Void) null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商家", description = "管理员权限，先下架所有活动")
    public Result<Void> deleteMerchant(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        merchantService.deleteMerchant(id);
        return Result.success("删除成功", (Void) null);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "商家状态管理", description = "管理员权限")
    public Result<Void> updateMerchantStatus(
            @PathVariable Long id,
            @Valid @RequestBody MerchantDTO.StatusRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        merchantService.updateMerchantStatus(id, req);
        return Result.success("状态更新成功", (Void) null);
    }

    // ========== 统计 ==========

    @GetMapping("/statistics")
    @Operation(summary = "特惠数据统计", description = "管理员权限")
    public Result<MerchantDTO.StatisticsVO> getStatistics(
            @Parameter(description = "统计开始日期")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "统计结束日期")
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(merchantService.getStatistics(startDate, endDate));
    }

    // ========== 商家评价 ==========

    @PostMapping("/review")
    @Operation(summary = "发表评价", description = "需登录，同一用户对同一商家只能发表一条评价")
    public Result<Long> createReview(
            @Valid @RequestBody MerchantDTO.ReviewRequest req,
            HttpServletRequest httpRequest) {
        return Result.success("评价成功", merchantService.createReview(req, getUserId(httpRequest)));
    }

    @GetMapping("/review/list/{merchantId}")
    @Operation(summary = "商家评价列表", description = "公开接口，返回评价列表及评分统计")
    public Result<PageResponse<MerchantDTO.ReviewPageVO>> getReviewList(
            @PathVariable Long merchantId,
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "筛选评分")
            @RequestParam(required = false) Integer score) {
        return Result.success(merchantService.getReviewList(merchantId, current, size, score));
    }

    @DeleteMapping("/review/{id}")
    @Operation(summary = "删除评价", description = "评价者或管理员")
    public Result<Void> deleteReview(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        merchantService.deleteReview(id, getUserId(httpRequest), isAdmin(httpRequest));
        return Result.success("删除成功", (Void) null);
    }

    @GetMapping("/review/admin/list")
    @Operation(summary = "评价管理列表", description = "管理员权限")
    public Result<PageResponse<MerchantDTO.ReviewVO>> getAdminReviewList(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "商家ID")
            @RequestParam(required = false) Long merchantId,
            @Parameter(description = "状态：1-正常 2-已删除")
            @RequestParam(required = false) Integer status,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(merchantService.getAdminReviewList(current, size, merchantId, status));
    }
}
