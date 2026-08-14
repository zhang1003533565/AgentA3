package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.SecondhandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/secondhand")
@Tag(name = "校园旧物", description = "二手物品发布、出售、收藏接口")
public class SecondhandController {

    @Autowired private SecondhandService secondhandService;

    private static final String ROLE_ADMIN = "ADMIN";

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) userId;
    }

    private boolean isAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        return ROLE_ADMIN.equals(role);
    }

    // ========== 分类 ==========

    @GetMapping("/category/list")
    @Operation(summary = "分类列表", description = "公开接口")
    public Result<List<SecondhandDTO.CategoryVO>> listCategories() {
        return Result.success(secondhandService.listCategories());
    }

    @PostMapping("/category")
    @Operation(summary = "新增分类", description = "管理员权限")
    public Result<SecondhandDTO.CategoryVO> createCategory(
            @Valid @RequestBody SecondhandDTO.CategoryRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success("创建成功", secondhandService.createCategory(req));
    }

    @PutMapping("/category/{id}")
    @Operation(summary = "编辑分类", description = "管理员权限")
    public Result<Void> updateCategory(
            @PathVariable Long id,
            @RequestBody SecondhandDTO.CategoryRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        secondhandService.updateCategory(id, req);
        return Result.success("更新成功", (Void) null);
    }

    @DeleteMapping("/category/{id}")
    @Operation(summary = "删除分类", description = "管理员权限，检查分类下是否有物品")
    public Result<Void> deleteCategory(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        secondhandService.deleteCategory(id);
        return Result.success("删除成功", (Void) null);
    }

    // ========== 物品 ==========

    @GetMapping("/item/list")
    @Operation(summary = "物品列表（分页筛选）", description = "公开接口")
    public Result<PageResponse<SecondhandDTO.ItemVO>> getItemList(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "分类ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "关键词")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "新旧程度")
            @RequestParam(required = false) Integer condition,
            @Parameter(description = "最低价格")
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @Parameter(description = "最高价格")
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @Parameter(description = "排序：latest/price_asc/price_desc/hot")
            @RequestParam(required = false) String sort) {
        return Result.success(secondhandService.getItemList(current, size, categoryId, keyword, condition, minPrice, maxPrice, sort));
    }

    @GetMapping("/item/{id}")
    @Operation(summary = "物品详情", description = "公开接口，自动浏览量+1")
    public Result<SecondhandDTO.ItemDetailVO> getItemDetail(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = null;
        try { userId = getUserId(httpRequest); } catch (Exception ignored) {}
        return Result.success(secondhandService.getItemDetail(id, userId));
    }

    @PostMapping("/item")
    @Operation(summary = "发布物品", description = "需登录")
    public Result<SecondhandDTO.ItemVO> createItem(
            @Valid @RequestBody SecondhandDTO.ItemRequest req,
            HttpServletRequest httpRequest) {
        return Result.success("发布成功", secondhandService.createItem(req, getUserId(httpRequest)));
    }

    @PutMapping("/item/{id}")
    @Operation(summary = "编辑物品", description = "需为物品所有者")
    public Result<Void> updateItem(
            @PathVariable Long id,
            @RequestBody SecondhandDTO.ItemRequest req,
            HttpServletRequest httpRequest) {
        secondhandService.updateItem(id, req, getUserId(httpRequest));
        return Result.success("更新成功", (Void) null);
    }

    @DeleteMapping("/item/{id}")
    @Operation(summary = "删除物品", description = "所有者或管理员")
    public Result<Void> deleteItem(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        secondhandService.deleteItem(id, getUserId(httpRequest), isAdmin(httpRequest));
        return Result.success("删除成功", (Void) null);
    }

    @GetMapping("/item/my")
    @Operation(summary = "我的发布", description = "需登录")
    public Result<PageResponse<SecondhandDTO.ItemVO>> getMyItems(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "物品状态")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "交易类型：sell-出物，buy-收物")
            @RequestParam(required = false) String tradeType,
            HttpServletRequest httpRequest) {
        return Result.success(secondhandService.getMyItems(getUserId(httpRequest), current, size, status, tradeType));
    }

    @GetMapping("/user/{userId}/items")
    @Operation(summary = "用户公开商品列表", description = "公开接口，查询指定用户发布的在线商品")
    public Result<PageResponse<SecondhandDTO.ItemVO>> getUserPublicItems(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(secondhandService.getUserPublicItems(userId, current, size));
    }

    @PutMapping("/item/{id}/offline")
    @Operation(summary = "物品下架")
    public Result<Void> offlineItem(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        secondhandService.offlineItem(id, getUserId(httpRequest), isAdmin(httpRequest));
        return Result.success("下架成功", (Void) null);
    }

    @PutMapping("/item/{id}/online")
    @Operation(summary = "物品重新上架")
    public Result<Void> onlineItem(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        secondhandService.onlineItem(id, getUserId(httpRequest), isAdmin(httpRequest));
        return Result.success("重新上架成功", (Void) null);
    }

    @PutMapping("/item/{id}/sold")
    @Operation(summary = "标记物品已售出")
    public Result<Void> soldItem(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        secondhandService.soldItem(id, getUserId(httpRequest), isAdmin(httpRequest));
        return Result.success("已标记为已售出", (Void) null);
    }

    @GetMapping("/item/admin/list")
    @Operation(summary = "后台物品列表", description = "管理员权限")
    public Result<PageResponse<SecondhandDTO.ItemVO>> getAdminList(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "关键词")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "分类ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "物品状态")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "交易类型: sell-出物 buy-收物")
            @RequestParam(required = false) String tradeType,
            @Parameter(description = "发布者ID")
            @RequestParam(required = false) Long userId,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(secondhandService.getAdminList(current, size, keyword, categoryId, status, tradeType, userId));
    }

    @PutMapping("/item/batch")
    @Operation(summary = "物品批量操作", description = "管理员权限")
    public Result<Void> batchOperation(
            @Valid @RequestBody SecondhandDTO.BatchRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        secondhandService.batchOperation(req);
        return Result.success("操作成功，共处理 " + req.getIds().size() + " 个物品", (Void) null);
    }

    // ========== 收藏 ==========

    @PostMapping("/favorite/{itemId}")
    @Operation(summary = "收藏物品", description = "需登录")
    public Result<Void> favoriteItem(
            @PathVariable Long itemId,
            HttpServletRequest httpRequest) {
        secondhandService.favoriteItem(itemId, getUserId(httpRequest));
        return Result.success("收藏成功", (Void) null);
    }

    @DeleteMapping("/favorite/{itemId}")
    @Operation(summary = "取消收藏", description = "需登录")
    public Result<Void> unfavoriteItem(
            @PathVariable Long itemId,
            HttpServletRequest httpRequest) {
        secondhandService.unfavoriteItem(itemId, getUserId(httpRequest));
        return Result.success("取消收藏成功", (Void) null);
    }

    @GetMapping("/favorite/my")
    @Operation(summary = "我的收藏列表", description = "需登录")
    public Result<PageResponse<SecondhandDTO.ItemVO>> getMyFavorites(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(secondhandService.getMyFavorites(getUserId(httpRequest), current, size));
    }

    // ========== 浏览历史 ==========

    @PostMapping("/browse-history/{itemId}")
    @Operation(summary = "记录浏览历史", description = "需登录，已存在则更新浏览时间")
    public Result<Void> recordBrowseHistory(
            @PathVariable Long itemId,
            HttpServletRequest httpRequest) {
        secondhandService.recordBrowseHistory(getUserId(httpRequest), itemId);
        return Result.success("记录成功", (Void) null);
    }

    @GetMapping("/browse-history/my")
    @Operation(summary = "我的浏览历史", description = "需登录")
    public Result<PageResponse<SecondhandDTO.BrowseHistoryVO>> getMyBrowseHistory(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(secondhandService.getBrowseHistory(getUserId(httpRequest), current, size));
    }

    @DeleteMapping("/browse-history/my")
    @Operation(summary = "清空浏览历史", description = "需登录")
    public Result<Void> clearMyBrowseHistory(HttpServletRequest httpRequest) {
        secondhandService.clearBrowseHistory(getUserId(httpRequest));
        return Result.success("清空成功", (Void) null);
    }

    // ========== 统计 ==========

    @GetMapping("/statistics")
    @Operation(summary = "旧物数据统计", description = "管理员权限")
    public Result<SecondhandDTO.StatisticsVO> getStatistics(
            @Parameter(description = "统计开始日期")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "统计结束日期")
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(secondhandService.getStatistics(startDate, endDate));
    }
}
