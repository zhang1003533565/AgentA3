package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dish")
public class Dish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '菜品名称'")
    private String name;

    @Column(name = "stall_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属档口 ID'")
    private Long stallId;

    @Column(name = "price", nullable = false, columnDefinition = "DECIMAL(10,2) NOT NULL COMMENT '菜品价格'")
    private BigDecimal price;

    @Column(name = "category", length = 50, columnDefinition = "VARCHAR(50) COMMENT '菜品分类'")
    private String category;

    @Column(name = "image_url", length = 255, columnDefinition = "VARCHAR(255) COMMENT '菜品图片 URL'")
    private String imageUrl;

    @Column(name = "rating", columnDefinition = "DECIMAL(3,2) DEFAULT 0 COMMENT '菜品评分 (0-5)'")
    private BigDecimal rating;

    @Column(name = "sold_count", columnDefinition = "INT DEFAULT 0 COMMENT '销量'")
    private Integer soldCount;

    @Column(name = "is_available", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1 COMMENT '是否可用：1-可售 0-停售'")
    private Boolean isAvailable = true;

    @Column(name = "taste", length = 100, columnDefinition = "VARCHAR(100) COMMENT '口味类型'")
    private String taste;

    @Column(name = "description", columnDefinition = "TEXT COMMENT '菜品描述'")
    private String description;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stall_id", insertable = false, updatable = false)
    private CanteenStall stall;
}