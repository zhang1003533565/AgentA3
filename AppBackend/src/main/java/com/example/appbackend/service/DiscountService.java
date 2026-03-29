package com.example.appbackend.service;

import com.example.appbackend.dto.*;

public interface DiscountService {

    // ========== 优惠活动 ==========
    DiscountDTO.ActivityVO createActivity(DiscountDTO.ActivityRequest req, Long currentUserId);
    PageResponse<DiscountDTO.ActivityVO> getActivityList(Integer current, Integer size, Long merchantId,
                                                          Long categoryId, String keyword,
                                                          Integer status,
                                                          java.math.BigDecimal lat, java.math.BigDecimal lng,
                                                          String sort, Long currentUserId);
    DiscountDTO.ActivityDetailVO getActivityDetail(Long id, Long currentUserId);
    void updateActivity(Long id, DiscountDTO.ActivityRequest req, Long currentUserId);
    void deleteActivity(Long id, Long currentUserId);
    PageResponse<DiscountDTO.ActivityVO> getMerchantActivities(Long merchantId, Integer current, Integer size, Long currentUserId);
    void offlineActivity(Long id, Long currentUserId);

    // ========== 领取 ==========
    void claimActivity(Long activityId, Long userId);
    PageResponse<DiscountDTO.ClaimVO> getMyClaims(Long userId, Integer current, Integer size);

    // ========== 收藏 ==========
    void favoriteActivity(Long activityId, Long userId);
    void unfavoriteActivity(Long activityId, Long userId);
    PageResponse<DiscountDTO.ActivityVO> getMyFavoriteActivities(Long userId, Integer current, Integer size);
}
