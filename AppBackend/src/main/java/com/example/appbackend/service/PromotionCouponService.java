package com.example.appbackend.service;

import com.example.appbackend.dto.PromotionCouponDTO;
import com.example.appbackend.dto.PromotionCouponClaimRequest;
import com.example.appbackend.dto.UserCouponDTO;

import java.util.List;

/**
 * 优惠券 Service
 */
public interface PromotionCouponService {

    /**
     * 获取优惠券列表
     */
    List<PromotionCouponDTO> getAllCoupons();

    /**
     * 根据分类获取优惠券
     */
    List<PromotionCouponDTO> getCouponsByCategory(String category);

    /**
     * 根据商家 ID 获取优惠券
     */
    List<PromotionCouponDTO> getCouponsByMerchantId(Long merchantId);

    /**
     * 根据档口 ID 获取优惠券
     */
    List<PromotionCouponDTO> getCouponsByStallId(Long stallId);

    /**
     * 根据设施 ID 获取优惠券
     */
    List<PromotionCouponDTO> getCouponsByFacilityId(Long facilityId);

    /**
     * 根据标签获取优惠券
     */
    List<PromotionCouponDTO> getCouponsByTagType(String tagType);

    /**
     * 根据 ID 获取优惠券详情
     */
    PromotionCouponDTO getCouponById(Long id);

    /**
     * 创建优惠券
     */
    PromotionCouponDTO createCoupon(PromotionCouponDTO request);

    /**
     * 更新优惠券
     */
    PromotionCouponDTO updateCoupon(Long id, PromotionCouponDTO request);

    /**
     * 删除优惠券
     */
    void deleteCoupon(Long id);

    /**
     * 领取优惠券
     */
    void claimCoupon(Long couponId, Long userId, PromotionCouponClaimRequest request);

    /**
     * 查询 Banner 展示的优惠券
     */
    List<PromotionCouponDTO> getBannerCoupons();

    /**
     * 查询我的优惠券
     */
    List<UserCouponDTO> getMyCoupons(Long userId);
}
