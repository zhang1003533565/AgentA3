package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import java.util.List;

public interface SecondhandService {

    // ========== 分类 ==========
    List<SecondhandDTO.CategoryVO> listCategories();
    SecondhandDTO.CategoryVO createCategory(SecondhandDTO.CategoryRequest req);
    void updateCategory(Long id, SecondhandDTO.CategoryRequest req);
    void deleteCategory(Long id);

    // ========== 物品 ==========
    PageResponse<SecondhandDTO.ItemVO> getItemList(Integer current, Integer size, Long categoryId, String keyword,
                                                    Integer condition, java.math.BigDecimal minPrice,
                                                    java.math.BigDecimal maxPrice, String sort);
    SecondhandDTO.ItemDetailVO getItemDetail(Long id, Long currentUserId);
    SecondhandDTO.ItemVO createItem(SecondhandDTO.ItemRequest req, Long userId);
    void updateItem(Long id, SecondhandDTO.ItemRequest req, Long userId);
    void deleteItem(Long id, Long userId, boolean isAdmin);
    PageResponse<SecondhandDTO.ItemVO> getMyItems(Long userId, Integer current, Integer size, Integer status);
    void offlineItem(Long id, Long userId, boolean isAdmin);
    void onlineItem(Long id, Long userId, boolean isAdmin);
    void soldItem(Long id, Long userId, boolean isAdmin);
    PageResponse<SecondhandDTO.ItemVO> getAdminList(Integer current, Integer size, String keyword,
                                                     Long categoryId, Integer status, Long userId);
    void batchOperation(SecondhandDTO.BatchRequest req);

    // ========== 收藏 ==========
    void favoriteItem(Long itemId, Long userId);
    void unfavoriteItem(Long itemId, Long userId);
    PageResponse<SecondhandDTO.ItemVO> getMyFavorites(Long userId, Integer current, Integer size);

    // ========== 统计 ==========
    SecondhandDTO.StatisticsVO getStatistics(String startDate, String endDate);
}
