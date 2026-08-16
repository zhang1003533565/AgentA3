package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "secondhand_item")
@Schema(description = "二手物品实体")
public class SecondhandItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "物品ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '发布者ID'")
    @Schema(description = "发布者ID", example = "5")
    private Long userId;

    @Column(name = "category_id", columnDefinition = "BIGINT COMMENT '分类ID'")
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '物品标题'")
    @Schema(description = "物品标题", example = "iPad Air 4 256G", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Column(columnDefinition = "TEXT COMMENT '物品描述'")
    @Schema(description = "物品描述", example = "2023年购买，功能正常...")
    private String description;

    @Column(columnDefinition = "TEXT COMMENT '图片URL列表(JSON数组)'")
    @Schema(description = "图片URL列表(JSON数组)", example = "[\"https://cdn.example.com/img1.jpg\"]")
    private String images;

    @Column(precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) COMMENT '售价'")
    @Schema(description = "售价", example = "2800.00")
    private BigDecimal price;

    @Column(name = "original_price", precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) COMMENT '原价'")
    @Schema(description = "原价", example = "4999.00")
    private BigDecimal originalPrice;

    @Column(name = "`condition`", columnDefinition = "INT COMMENT '新旧程度: 1-全新 2-几乎全新 3-轻微使用痕迹 4-明显使用痕迹 5-仅限零件'")
    @Schema(description = "新旧程度: 1-全新 2-几乎全新 3-轻微使用痕迹 4-明显使用痕迹 5-仅限零件", example = "3")
    private Integer condition;

    @Column(length = 200, columnDefinition = "VARCHAR(200) COMMENT '期望交易地点'")
    @Schema(description = "期望交易地点", example = "图书馆门口")
    private String location;

    @Column(name = "campus_id", length = 50, columnDefinition = "VARCHAR(50) COMMENT '校区ID'")
    @Schema(description = "校区ID", example = "main")
    private String campusId;

    @Column(name = "campus_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '校区名称'")
    @Schema(description = "校区名称", example = "主校区")
    private String campusName;

    @Column(name = "trade_location", length = 100, columnDefinition = "VARCHAR(100) COMMENT '交易区域'")
    @Schema(description = "交易区域", example = "teaching_m")
    private String tradeLocation;

    @Column(name = "pickup_point", length = 200, columnDefinition = "VARCHAR(200) COMMENT '自提点'")
    @Schema(description = "自提点", example = "三教门口")
    private String pickupPoint;

    @Column(name = "trade_type", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'sell' COMMENT '交易类型: sell-出物 buy-收物'")
    @Schema(description = "交易类型: sell-出物 buy-收物", example = "sell")
    private String tradeType = "sell";

    @Column(name = "view_count", columnDefinition = "INT DEFAULT 0 COMMENT '浏览量'")
    @Schema(description = "浏览量", example = "128")
    private Integer viewCount = 0;

    @Column(name = "favorite_count", columnDefinition = "INT DEFAULT 0 COMMENT '收藏数'")
    @Schema(description = "收藏数", example = "15")
    private Integer favoriteCount = 0;

    @Column(name = "inquiry_count", columnDefinition = "INT DEFAULT 0 COMMENT '咨询次数'")
    @Schema(description = "咨询次数", example = "8")
    private Integer inquiryCount = 0;

    @Column(name = "heat_score", columnDefinition = "INT DEFAULT 0 COMMENT '热度分 = 浏览*1 + 收藏*3 + 咨询*5'")
    @Schema(description = "热度分", example = "128")
    private Integer heatScore = 0;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 2 COMMENT '状态: 2-在售 3-已售出 4-已下架'")
    @Schema(description = "状态: 2-在售 3-已售出 4-已下架", example = "2")
    private Integer status = 2;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private SecondhandCategory category;

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
