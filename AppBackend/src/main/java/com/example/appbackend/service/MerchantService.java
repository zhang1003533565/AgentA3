package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import java.util.List;

public interface MerchantService {

    // ========== 商家分类 ==========
    List<MerchantDTO.CategoryVO> listCategories();
    MerchantDTO.CategoryVO createCategory(MerchantDTO.CategoryRequest req);
    void updateCategory(Long id, MerchantDTO.CategoryRequest req);
    void deleteCategory(Long id);

    // ========== 商家 ==========
    PageResponse<MerchantDTO.MerchantVO> getMerchantList(Integer current, Integer size, Long categoryId,
                                                          String keyword, Integer status,
                                                          java.math.BigDecimal lat, java.math.BigDecimal lng,
                                                          String sort);
    MerchantDTO.MerchantDetailVO getMerchantDetail(Long id);
    MerchantDTO.MerchantVO createMerchant(MerchantDTO.MerchantRequest req);
    void updateMerchant(Long id, MerchantDTO.MerchantRequest req);
    void deleteMerchant(Long id);
    void updateMerchantStatus(Long id, MerchantDTO.StatusRequest req);

    // ========== 统计 ==========
    MerchantDTO.StatisticsVO getStatistics(String startDate, String endDate);

    // ========== 商家评价 ==========
    Long createReview(MerchantDTO.ReviewRequest req, Long userId);
    PageResponse<MerchantDTO.ReviewPageVO> getReviewList(Long merchantId, Integer current, Integer size, Integer score);
    void deleteReview(Long id, Long userId, boolean isAdmin);
    PageResponse<MerchantDTO.ReviewVO> getAdminReviewList(Integer current, Integer size, Long merchantId, Integer status);
}
