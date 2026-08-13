package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.DiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discount")
@Tag(name = "优惠活动", description = "优惠活动发布、领取接口")
public class DiscountController {

    @Autowired private DiscountService discountService;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MERCHANT = "MERCHANT";

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) userId;
    }

    private boolean isAdminOrMerchant(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        return ROLE_ADMIN.equals(role) || ROLE_MERCHANT.equals(role);
    }

    // ========== 优惠活动 ==========

    @PostMapping("/activity")
    @Operation(summary = "发布优惠活动", description = "商家或管理员权限")
    public Result<DiscountDTO.ActivityVO> createActivity(
            @Valid @RequestBody DiscountDTO.ActivityRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        Long currentUserId = getUserId(httpRequest);
        return Result.success("发布成功", discountService.createActivity(req, currentUserId));
    }

    @GetMapping("/activity/list")
    @Operation(summary = "优惠活动列表（分页筛选）", description = "公开接口，只返回有剩余名额的活动，由前端根据 statusText 判断显示")
    public Result<PageResponse<DiscountDTO.ActivityVO>> getActivityList(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "商家ID")
            @RequestParam(required = false) Long merchantId,
            @Parameter(description = "商家分类ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "关键词")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "活动状态：1-进行中 2-已结束")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "用户纬度")
            @RequestParam(required = false) java.math.BigDecimal lat,
            @Parameter(description = "用户经度")
            @RequestParam(required = false) java.math.BigDecimal lng,
            @Parameter(description = "排序：latest/hot/expiring（即将到期）/distance")
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {
        Long userId = null;
        try { userId = getUserId(httpRequest); } catch (Exception ignored) {}
        return Result.success(discountService.getActivityList(current, size, merchantId, categoryId, keyword,
                status, lat, lng, sort, userId));
    }

    @GetMapping("/activity/{id}")
    @Operation(summary = "优惠活动详情", description = "公开接口")
    public Result<DiscountDTO.ActivityDetailVO> getActivityDetail(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = null;
        try { userId = getUserId(httpRequest); } catch (Exception ignored) {}
        return Result.success(discountService.getActivityDetail(id, userId));
    }

    @PutMapping("/activity/{id}")
    @Operation(summary = "编辑优惠活动", description = "商家管理员或超级管理员")
    public Result<Void> updateActivity(
            @PathVariable Long id,
            @Valid @RequestBody DiscountDTO.ActivityRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        discountService.updateActivity(id, req, getUserId(httpRequest));
        return Result.success("更新成功", (Void) null);
    }

    @DeleteMapping("/activity/{id}")
    @Operation(summary = "删除优惠活动", description = "商家管理员或超级管理员")
    public Result<Void> deleteActivity(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        discountService.deleteActivity(id, getUserId(httpRequest));
        return Result.success("删除成功", (Void) null);
    }

    @GetMapping("/activity/merchant/{merchantId}")
    @Operation(summary = "商家优惠活动列表", description = "公开接口，返回该商家所有活动")
    public Result<PageResponse<DiscountDTO.ActivityVO>> getMerchantActivities(
            @PathVariable Long merchantId,
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest httpRequest) {
        Long userId = null;
        try { userId = getUserId(httpRequest); } catch (Exception ignored) {}
        return Result.success(discountService.getMerchantActivities(merchantId, current, size, userId));
    }

    @PutMapping("/activity/{id}/offline")
    @Operation(summary = "优惠活动下架", description = "商家管理员或超级管理员")
    public Result<Void> offlineActivity(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        discountService.offlineActivity(id, getUserId(httpRequest));
        return Result.success("下架成功", (Void) null);
    }

    @PutMapping("/activity/{id}/end-early")
    @Operation(summary = "提前结束优惠活动", description = "管理员将活动状态改为已领完")
    public Result<Void> endActivityEarly(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        discountService.endActivityEarly(id, getUserId(httpRequest));
        return Result.success("活动已提前结束", (Void) null);
    }

    // ========== 领取 ==========

    @PostMapping("/claim/{activityId}")
    @Operation(summary = "领取优惠活动", description = "需登录")
    public Result<Void> claimActivity(
            @PathVariable Long activityId,
            HttpServletRequest httpRequest) {
        discountService.claimActivity(activityId, getUserId(httpRequest));
        return Result.success("领取成功", (Void) null);
    }

    @GetMapping("/claim/my")
    @Operation(summary = "我的领取列表", description = "需登录")
    public Result<PageResponse<DiscountDTO.ClaimVO>> getMyClaims(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(discountService.getMyClaims(getUserId(httpRequest), current, size));
    }

    // ========== 收藏 ==========

    @PostMapping("/favorite/{activityId}")
    @Operation(summary = "收藏优惠活动", description = "需登录")
    public Result<Void> favoriteActivity(
            @PathVariable Long activityId,
            HttpServletRequest httpRequest) {
        discountService.favoriteActivity(activityId, getUserId(httpRequest));
        return Result.success("收藏成功", (Void) null);
    }

    @DeleteMapping("/favorite/{activityId}")
    @Operation(summary = "取消收藏优惠活动", description = "需登录")
    public Result<Void> unfavoriteActivity(
            @PathVariable Long activityId,
            HttpServletRequest httpRequest) {
        discountService.unfavoriteActivity(activityId, getUserId(httpRequest));
        return Result.success("取消收藏成功", (Void) null);
    }

    @GetMapping("/favorite/my")
    @Operation(summary = "我的优惠收藏列表", description = "需登录")
    public Result<PageResponse<DiscountDTO.ActivityVO>> getMyFavoriteActivities(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(discountService.getMyFavoriteActivities(getUserId(httpRequest), current, size));
    }
}
