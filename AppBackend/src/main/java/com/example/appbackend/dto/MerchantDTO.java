package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "商家特惠 - 商家相关请求/响应")
public class MerchantDTO {

    @Data
    @Schema(description = "商家分类请求")
    public static class CategoryRequest {
        @NotBlank(message = "分类名称不能为空")
        @Size(min = 2, max = 20, message = "分类名称2-20字")
        @Schema(description = "分类名称", example = "休闲娱乐")
        private String categoryName;

        @Schema(description = "排序值，默认0", example = "1")
        private Integer sort = 0;
    }

    @Data
    @Schema(description = "商家分类响应")
    public static class CategoryVO {
        private Long id;
        private String categoryName;
        private Integer sort;
        private Integer status;
        private String statusText;
    }

    @Data
    @Schema(description = "新增/编辑商家请求")
    public static class MerchantRequest {
        @NotBlank(message = "商家名称不能为空")
        @Size(min = 2, max = 30, message = "商家名称2-30字")
        @Schema(description = "商家名称", example = "学府餐厅")
        private String merchantName;

        @NotNull(message = "分类ID不能为空")
        @Schema(description = "分类ID", example = "1")
        private Long categoryId;

        @Size(min = 10, max = 500, message = "商家介绍10-500字")
        @Schema(description = "商家介绍")
        private String description;

        @Schema(description = "商家Logo URL")
        private String logo;

        @Size(max = 9, message = "最多9张图片")
        @Schema(description = "商家环境图片，最多9张")
        private List<String> images;

        @NotBlank(message = "地址不能为空")
        @Schema(description = "商家地址", example = "学校北门向东200米")
        private String address;

        @Schema(description = "经度")
        private BigDecimal longitude;

        @Schema(description = "纬度")
        private BigDecimal latitude;

        @Schema(description = "联系人")
        private String contactName;

        @NotBlank(message = "联系电话不能为空")
        @Schema(description = "联系电话", example = "13812345678")
        private String contactPhone;

        @Schema(description = "营业时间", example = "09:00-21:00")
        private String businessHours;

        @Schema(description = "商家登录账号", example = "merchant001")
        private String username;

        @Schema(description = "商家登录密码", example = "pass123456")
        private String password;
    }

    @Data
    @Schema(description = "商家列表项响应")
    public static class MerchantVO {
        private Long id;
        private String merchantName;
        private Long categoryId;
        private String categoryName;
        private String logo;
        private String address;
        private Double distance;
        private String contactPhone;
        private String businessHours;
        private Integer status;
        private String statusText;
        private Integer activityCount;
        private Double avgScore;
        private Integer reviewCount;
        @Schema(description = "商家登录账号（仅创建商家时返回）")
        private String merchantUsername;
        @Schema(description = "商家登录密码（仅创建商家时返回）")
        private String merchantPassword;
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    @Schema(description = "商家详情响应")
    public static class MerchantDetailVO extends MerchantVO {
        private String description;
        private List<String> images;
        private String contactName;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer viewCount;
        private List<DiscountDTO.ActivityVO> activities;
    }

    @Data
    @Schema(description = "商家状态更新请求")
    public static class StatusRequest {
        @NotNull(message = "状态不能为空")
        @Schema(description = "状态：1-正常营业 2-暂停营业 3-已禁用", example = "2")
        private Integer status;
    }

    @Data
    @Schema(description = "商家统计数据")
    public static class StatisticsVO {
        private Long totalMerchants;
        private Long totalActivities;
        private Long activeActivities;
        private Long totalReviews;
        private Double avgScore;
        private List<CountItem> topMerchants;
        private List<CountItem> activityTrend;
    }

    // ========== 商家评价 ==========

    @Data
    @Schema(description = "发表评价请求")
    public static class ReviewRequest {
        @NotNull(message = "商家ID不能为空")
        @Schema(description = "商家ID", example = "3")
        private Long merchantId;

        @Schema(description = "关联优惠活动ID（可选）", example = "5")
        private Long activityId;

        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低1分")
        @Max(value = 5, message = "评分最高5分")
        @Schema(description = "评分（1-5分）", example = "5")
        private Integer score;

        @NotBlank(message = "评价内容不能为空")
        @Size(min = 5, max = 500, message = "评价内容5-500字")
        @Schema(description = "评价内容", example = "餐厅环境很好，价格实惠...")
        private String content;

        @Size(max = 9, message = "最多9张图片")
        @Schema(description = "评价图片，最多9张")
        private List<String> images;
    }

    @Data
    @Schema(description = "商家评价列表项响应")
    public static class ReviewVO {
        private Long id;
        private Long userId;
        private String username;
        private String userAvatar;
        private Long merchantId;
        private Long activityId;
        private Integer score;
        private String content;
        private List<String> images;
        private Integer status;
        private String statusText;
        private String createTime;
    }

    @Data
    @Schema(description = "商家评价列表响应（带统计）")
    public static class ReviewPageVO {
        private List<ReviewVO> records;
        private Long total;
        private Integer size;
        private Integer current;
        private Integer pages;
        private Double avgScore;
        private Map<String, Long> scoreDistribution;
    }
}
