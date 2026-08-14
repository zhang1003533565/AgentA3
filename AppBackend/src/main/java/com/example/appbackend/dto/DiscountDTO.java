package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "商家特惠 - 优惠活动相关请求/响应")
public class DiscountDTO {

    @Data
@Schema(description = "发布/编辑优惠活动请求")
public static class ActivityRequest {
    @NotNull(message = "商家ID不能为空")
    @Schema(description = "商家ID", example = "3")
    private Long merchantId;

    @NotBlank(message = "活动标题不能为空")
    @Size(min = 4, max = 50, message = "活动标题4-50字")
    @Schema(description = "活动标题", example = "午餐特价套餐")
    private String title;

    @NotBlank(message = "活动描述不能为空")
    @Size(min = 10, max = 500, message = "活动描述10-500字")
    @Schema(description = "活动描述")
    private String description;

    @Schema(description = "封面图片URL")
    private String coverImage;

    @Size(max = 9, message = "最多9张图片")
    @Schema(description = "活动图片，最多9张")
    private List<String> images;

    @NotBlank(message = "开始时间不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
            message = "开始时间格式须为 yyyy-MM-dd HH:mm:ss")
    @Schema(description = "活动开始时间", example = "2025-03-27 11:00:00")
    private String startTime;

    @NotBlank(message = "结束时间不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
            message = "结束时间格式须为 yyyy-MM-dd HH:mm:ss")
    @Schema(description = "活动结束时间", example = "2025-04-30 23:59:59")
    private String endTime;

    @Schema(description = "使用规则")
    private String useRules;

    @Schema(description = "总名额")
    private Integer totalCount;

    @Schema(description = "剩余名额")
    private Integer remainCount;

    @Schema(description = "活动状态 0-未开始 1-进行中 2-已领完 3-已结束", example = "1")
    private Integer status;
}

    @Data
    @Schema(description = "优惠活动列表项响应")
    public static class ActivityVO {
        private Long id;
        private Long merchantId;
        private String merchantName;
        private String merchantLogo;
        private String title;
        private String description;
        private String coverImage;
        private String startTime;
        private String endTime;
        private Integer remainCount;
        private Double distance;
        private Integer status;
        private String statusText;
        private Boolean isFavorited;
        private String createTime;
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    @Schema(description = "优惠活动详情响应")
    public static class ActivityDetailVO extends ActivityVO {
        private List<String> images;
        private String startTime;
        private String useRules;
        private Integer totalCount;
        private Integer claimedCount;
        private String merchantAddress;
        private String merchantContactPhone;
    }

    @Data
    @Schema(description = "领取记录响应")
    public static class ClaimVO {
        private Long id;
        private Long activityId;
        private String title;
        private String coverImage;
        private String merchantName;
        private String merchantAddress;
        private String startTime;
        private String endTime;
        private String claimTime;
        private Integer status;
        private String statusText;
    }
}
