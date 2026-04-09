package com.example.appbackend.controller;

import com.example.appbackend.dto.PromotionCouponDTO;
import com.example.appbackend.dto.PromotionCouponClaimRequest;
import com.example.appbackend.dto.UserCouponDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PromotionCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券管理 Controller
 */
@RestController
@RequestMapping("/api/v1/promotion-coupon")
@Tag(name = "优惠券管理", description = "优惠券发布、领取接口")
public class PromotionCouponController {

    @Autowired
    private PromotionCouponService promotionCouponService;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MERCHANT = "MERCHANT";

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(401, "请先登录");
        return (Long) userId;
    }

    private String getUserRole(HttpServletRequest request) {
        return (String) request.getAttribute("role");
    }

    private boolean isAdminOrMerchant(HttpServletRequest request) {
        String role = getUserRole(request);
        return ROLE_ADMIN.equals(role) || ROLE_MERCHANT.equals(role);
    }

    // ========== 公开查询接口 ==========

    @GetMapping("/list")
    @Operation(summary = "优惠券列表", description = "公开接口，返回所有上架的优惠券")
    public Result<List<PromotionCouponDTO>> getCouponList(
            @Parameter(description = "分类：coupon-食堂优惠卡，card-校园卡，ad-代理服务，life-生活服务")
            @RequestParam(required = false) String category,
            @Parameter(description = "标签类型：new-新品，hot-热门，recommend-推荐")
            @RequestParam(required = false) String tagType,
            @Parameter(description = "商家 ID")
            @RequestParam(required = false) Long merchantId,
            @Parameter(description = "档口 ID")
            @RequestParam(required = false) Long stallId,
            @Parameter(description = "设施 ID")
            @RequestParam(required = false) Long facilityId) {
        List<PromotionCouponDTO> coupons;
        if (category != null && !category.isEmpty()) {
            coupons = promotionCouponService.getCouponsByCategory(category);
        } else if (tagType != null && !tagType.isEmpty()) {
            coupons = promotionCouponService.getCouponsByTagType(tagType);
        } else if (merchantId != null) {
            coupons = promotionCouponService.getCouponsByMerchantId(merchantId);
        } else if (stallId != null) {
            coupons = promotionCouponService.getCouponsByStallId(stallId);
        } else if (facilityId != null) {
            coupons = promotionCouponService.getCouponsByFacilityId(facilityId);
        } else {
            coupons = promotionCouponService.getAllCoupons();
        }
        return Result.success(coupons);
    }

    @GetMapping("/banner")
    @Operation(summary = "Banner 展示的优惠券", description = "公开接口")
    public Result<List<PromotionCouponDTO>> getBannerCoupons() {
        return Result.success(promotionCouponService.getBannerCoupons());
    }

    @GetMapping("/{id}")
    @Operation(summary = "优惠券详情", description = "公开接口")
    public Result<PromotionCouponDTO> getCouponDetail(@PathVariable Long id) {
        return Result.success(promotionCouponService.getCouponById(id));
    }

    // ========== 领取接口 ==========

    @PostMapping("/{id}/claim")
    @Operation(summary = "领取优惠券", description = "需登录")
    public Result<Void> claimCoupon(@PathVariable Long id,
                                    @RequestBody(required = false) PromotionCouponClaimRequest request,
                                    HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        promotionCouponService.claimCoupon(id, userId, request);
        return Result.success("领取成功", (Void) null);
    }

    @GetMapping("/my")
    @Operation(summary = "我的优惠券", description = "需登录")
    public Result<List<UserCouponDTO>> getMyCoupons(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(promotionCouponService.getMyCoupons(userId));
    }

    // ========== 管理后台接口（需权限）==========

    @PostMapping
    @Operation(summary = "创建优惠券", description = "商家管理员或超级管理员权限")
    public Result<PromotionCouponDTO> createCoupon(
            @Valid @RequestBody PromotionCouponDTO request,
            HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) {
            throw new BusinessException(403, "无权限");
        }
        return Result.success("创建成功", promotionCouponService.createCoupon(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新优惠券", description = "商家管理员或超级管理员权限")
    public Result<PromotionCouponDTO> updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody PromotionCouponDTO request,
            HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) {
            throw new BusinessException(403, "无权限");
        }
        return Result.success("更新成功", promotionCouponService.updateCoupon(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除优惠券", description = "商家管理员或超级管理员权限")
    public Result<Void> deleteCoupon(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!isAdminOrMerchant(httpRequest)) {
            throw new BusinessException(403, "无权限");
        }
        promotionCouponService.deleteCoupon(id);
        return Result.success("删除成功", (Void) null);
    }
}
