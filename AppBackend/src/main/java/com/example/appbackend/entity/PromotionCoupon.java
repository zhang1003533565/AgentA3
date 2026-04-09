package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 优惠券表
 */
@Data
@Entity
@Table(name = "promotion_coupon")
@Schema(description = "优惠券实体")
public class PromotionCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "优惠券 ID", example = "1")
    private Long id;

    @Column(name = "coupon_name", nullable = false, length = 100, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '优惠券名称'")
    @Schema(description = "优惠券名称", example = "食堂满减券", requiredMode = Schema.RequiredMode.REQUIRED)
    private String couponName;

    @Column(name = "category", length = 20, columnDefinition = "VARCHAR(20) COMMENT '分类：coupon-食堂优惠卡，card-校园卡，ad-代理服务，life-生活服务'")
    @Schema(description = "分类", example = "coupon")
    private String category;

    @Column(name = "merchant_id", columnDefinition = "BIGINT COMMENT '关联商家 ID'")
    @Schema(description = "关联商家 ID", example = "1")
    private Long merchantId;

    @Column(name = "stall_id", columnDefinition = "BIGINT COMMENT '关联档口 ID'")
    @Schema(description = "关联档口 ID", example = "2")
    private Long stallId;

    @Column(name = "facility_id", columnDefinition = "BIGINT COMMENT '关联设施 ID'")
    @Schema(description = "关联设施 ID", example = "3")
    private Long facilityId;

    @Column(name = "total_quantity", nullable = false, columnDefinition = "INT NOT NULL COMMENT '发放总量'")
    @Schema(description = "发放总量", example = "1000")
    private Integer totalQuantity;

    @Column(name = "start_date", columnDefinition = "DATE COMMENT '开始日期'")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "开始日期", example = "2026-03-01")
    private String startDate;

    @Column(name = "end_date", columnDefinition = "DATE COMMENT '结束日期'")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "结束日期", example = "2026-06-30")
    private String endDate;

    @Column(name = "image_url", length = 255, columnDefinition = "VARCHAR(255) COMMENT '图片 URL'")
    @Schema(description = "图片 URL", example = "https://example.com/coupon-1.png")
    private String imageUrl;

    @Column(name = "tag_type", length = 20, columnDefinition = "VARCHAR(20) COMMENT '标签：new-新品，hot-热门，recommend-推荐'")
    @Schema(description = "标签类型", example = "new")
    private String tagType;

    @Column(name = "pickup_location", length = 255, columnDefinition = "VARCHAR(255) COMMENT '线下领取位置'")
    @Schema(description = "线下领取位置", example = "第一学生餐厅一楼服务台")
    private String pickupLocation;

    @Column(name = "description", columnDefinition = "TEXT COMMENT '优惠券描述'")
    @Schema(description = "优惠券描述", example = "新学期优惠，全场通用")
    private String description;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-上架 2-下架'")
    @Schema(description = "状态", example = "1")
    private Integer status = 1;

    @Column(name = "sort_order", columnDefinition = "INT DEFAULT 0 COMMENT '排序值'")
    @Schema(description = "排序值", example = "1")
    private Integer sortOrder = 0;

    @Column(name = "is_banner", columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否 Banner 展示'")
    @Schema(description = "是否 Banner 展示", example = "true")
    private Boolean isBanner = false;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", insertable = false, updatable = false)
    @Schema(hidden = true)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stall_id", insertable = false, updatable = false)
    @Schema(hidden = true)
    private CanteenStall stall;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", insertable = false, updatable = false)
    @Schema(hidden = true)
    private CampusFacility facility;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
