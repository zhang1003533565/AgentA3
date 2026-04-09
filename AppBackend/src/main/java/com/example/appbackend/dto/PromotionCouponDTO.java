package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * 优惠券 DTO
 */
@Data
@Schema(description = "优惠券 DTO")
public class PromotionCouponDTO {

    @Schema(description = "优惠券 ID", example = "1")
    private Long id;

    @NotBlank(message = "优惠券名称不能为空")
    @Schema(description = "优惠券名称", example = "食堂满减券", requiredMode = Schema.RequiredMode.REQUIRED)
    private String couponName;

    @Schema(description = "分类：coupon-食堂优惠卡，card-校园卡，ad-代理服务，life-生活服务", example = "coupon")
    private String category;

    @Schema(description = "关联商家 ID", example = "1")
    private Long merchantId;

    @Schema(description = "关联档口 ID", example = "2")
    private Long stallId;

    @Schema(description = "关联设施 ID", example = "3")
    private Long facilityId;

    @Schema(description = "商家名称", example = "第一食堂")
    private String merchantName;

    @Schema(description = "档口名称", example = "早餐包子铺")
    private String stallName;

    @Schema(description = "设施名称", example = "第一学生餐厅")
    private String facilityName;

    @Schema(description = "发放总量", example = "1000")
    private Integer totalQuantity;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "开始日期", example = "2026-03-01")
    private String startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "结束日期", example = "2026-06-30")
    private String endDate;

    @Schema(description = "图片 URL", example = "https://example.com/coupon-1.png")
    private String imageUrl;

    @Schema(description = "标签类型：new-新品，hot-热门，recommend-推荐", example = "new")
    private String tagType;

    @Schema(description = "线下领取位置", example = "第一学生餐厅一楼服务台")
    private String pickupLocation;

    @Schema(description = "优惠券描述", example = "新学期优惠，全场通用")
    private String description;

    @Schema(description = "状态：1-上架 2-下架", example = "1")
    private Integer status = 1;

    @Schema(description = "排序值", example = "1")
    private Integer sortOrder = 0;

    @Schema(description = "是否 Banner 展示", example = "true")
    private Boolean isBanner = false;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
