package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "二手市场 - 物品相关请求")
public class SecondhandDTO {

    @Data
    @Schema(description = "发布/编辑物品请求")
    public static class ItemRequest {
        @NotNull(message = "分类ID不能为空")
        @Schema(description = "分类ID", example = "1")
        private Long categoryId;

        @NotBlank(message = "标题不能为空")
        @Size(min = 4, max = 50, message = "标题4-50字")
        @Schema(description = "物品标题", example = "iPad Air 4 256G 平板")
        private String title;

        @NotBlank(message = "描述不能为空")
        @Size(min = 10, max = 500, message = "描述10-500字")
        @Schema(description = "物品描述")
        private String description;

        @NotEmpty(message = "至少上传一张图片")
        @Size(max = 9, message = "最多9张图片")
        @Schema(description = "图片URL数组，最多9张")
        private List<String> images;

        @NotNull(message = "售价不能为空")
        @DecimalMin(value = "0.01", message = "售价最小0.01")
        @DecimalMax(value = "999999", message = "售价最大999999")
        @Schema(description = "售价", example = "2800.00")
        private BigDecimal price;

        @Schema(description = "原价", example = "4999.00")
        private BigDecimal originalPrice;

        @NotNull(message = "新旧程度不能为空")
        @Min(1) @Max(5)
        @Schema(description = "新旧程度：1-全新 2-几乎全新 3-轻微使用痕迹 4-明显使用痕迹 5-仅限零件", example = "3")
        private Integer condition;

        @Schema(description = "期望交易地点", example = "图书馆门口")
        private String location;
    }

    @Data
    @Schema(description = "物品列表项响应")
    public static class ItemVO {
        private Long id;
        private Long userId;
        private Long categoryId;
        private String categoryName;
        private String title;
        private String description;
        private List<String> images;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private Integer condition;
        private String conditionText;
        private String location;
        private Integer viewCount;
        private Integer favoriteCount;
        private Integer status;
        private String statusText;
        private String createTime;
        private SellerVO seller;
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    @Schema(description = "物品详情响应")
    public static class ItemDetailVO extends ItemVO {
        private String updateTime;
        private Boolean isFavorited;
    }

    @Data
    @Schema(description = "卖家信息")
    public static class SellerVO {
        private Long id;
        private String username;
        private String avatar;
        private String phone;
    }

    @Data
    @Schema(description = "分类请求")
    public static class CategoryRequest {
        @NotBlank(message = "分类名称不能为空")
        @Size(min = 2, max = 20, message = "分类名称2-20字")
        @Schema(description = "分类名称", example = "运动户外")
        private String categoryName;

        @Schema(description = "排序值，默认0", example = "1")
        private Integer sort = 0;
    }

    @Data
    @Schema(description = "分类响应")
    public static class CategoryVO {
        private Long id;
        private String categoryName;
        private Integer sort;
    }

    @Data
    @Schema(description = "物品批量操作请求")
    public static class BatchRequest {
        @NotEmpty(message = "ID列表不能为空")
        @Size(max = 100, message = "最多100个")
        @Schema(description = "物品ID数组，最多100个")
        private List<Long> ids;

        @NotBlank(message = "操作类型不能为空")
        @Schema(description = "操作类型：offline/delete", example = "offline")
        private String action;

        @Schema(description = "操作原因")
        private String reason;
    }

    @Data
    @Schema(description = "旧物统计数据")
    public static class StatisticsVO {
        private Long totalItems;
        private Long onSaleItems;
        private Long soldItems;
        private Long offlineItems;
        private List<CountItem> dailyPublishTrend;
        private List<CountItem> categoryDistribution;
    }
}
