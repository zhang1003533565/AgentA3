package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "merchant")
@Schema(description = "商家实体")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "商家ID", example = "1")
    private Long id;

    @Column(name = "merchant_name", nullable = false, length = 100, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '商家名称'")
    @Schema(description = "商家名称", example = "学府餐厅", requiredMode = Schema.RequiredMode.REQUIRED)
    private String merchantName;

    @Column(name = "category_id", columnDefinition = "BIGINT COMMENT '分类ID'")
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Column(columnDefinition = "TEXT COMMENT '商家介绍'")
    @Schema(description = "商家介绍", example = "学校北门旁的平价餐厅...")
    private String description;

    @Column(length = 255, columnDefinition = "VARCHAR(255) COMMENT '商家Logo URL'")
    @Schema(description = "商家Logo URL", example = "https://cdn.example.com/merchant/logo1.jpg")
    private String logo;

    @Column(columnDefinition = "TEXT COMMENT '商家环境图片列表(JSON数组)'")
    @Schema(description = "商家环境图片列表(JSON数组)", example = "[\"https://cdn.example.com/merchant/img1.jpg\"]")
    private String images;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '商家地址'")
    @Schema(description = "商家地址", example = "学校北门向东200米", requiredMode = Schema.RequiredMode.REQUIRED)
    private String address;

    @Column(precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '经度'")
    @Schema(description = "经度", example = "116.4074")
    private BigDecimal longitude;

    @Column(precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '纬度'")
    @Schema(description = "纬度", example = "39.9042")
    private BigDecimal latitude;

    @Column(name = "contact_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '联系人姓名'")
    @Schema(description = "联系人姓名", example = "李老板")
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL COMMENT '联系电话'")
    @Schema(description = "联系电话", example = "13812345678", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contactPhone;

    @Column(name = "business_hours", length = 50, columnDefinition = "VARCHAR(50) COMMENT '营业时间'")
    @Schema(description = "营业时间", example = "09:00-21:00")
    private String businessHours;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '关联商家用户ID'")
    @Schema(description = "关联商家用户ID", example = "10")
    private Long userId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常营业 2-暂停营业 3-已禁用'")
    @Schema(description = "状态: 1-正常营业 2-暂停营业 3-已禁用", example = "1")
    private Integer status = 1;

    @Column(name = "avg_score", precision = 2, scale = 1, columnDefinition = "DECIMAL(2,1) DEFAULT 0 COMMENT '平均评分'")
    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgScore = BigDecimal.ZERO;

    @Column(name = "review_count", columnDefinition = "INT DEFAULT 0 COMMENT '评价总数'")
    @Schema(description = "评价总数", example = "128")
    private Integer reviewCount = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private MerchantCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

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
