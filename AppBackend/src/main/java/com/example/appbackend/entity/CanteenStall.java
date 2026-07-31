package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "canteen_stall")
public class CanteenStall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stall_name", nullable = false, length = 100, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '档口名称'")
    private String stallName;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属餐厅 ID'")
    private Long restaurantId;

    @Column(name = "floor", length = 20, columnDefinition = "VARCHAR(20) COMMENT '楼层'")
    private String floor;

    @Column(name = "floor_id")
    private Long floorId;

    @Column(name = "category", length = 50, columnDefinition = "VARCHAR(50) COMMENT '品类/菜系'")
    private String category;

    @Column(name = "cuisine_id")
    private Long cuisineId;

    @Column(name = "location", length = 200, columnDefinition = "VARCHAR(200) COMMENT '位置描述'")
    private String location;

    @Column(name = "score", columnDefinition = "DECIMAL(3,2) DEFAULT 0 COMMENT '评分 (0-5)'")
    private BigDecimal score;

    @Column(name = "review_count", columnDefinition = "INT DEFAULT 0 COMMENT '评价总数'")
    private Integer reviewCount;

    @Column(name = "recommend_rate", columnDefinition = "INT DEFAULT 0 COMMENT '推荐率 (%)'")
    private Integer recommendRate;

    @Column(name = "avg_price", columnDefinition = "DECIMAL(10,2) COMMENT '人均价格'")
    private BigDecimal avgPrice;

    @Column(name = "business_hours", length = 100, columnDefinition = "VARCHAR(100) COMMENT '营业时间'")
    private String businessHours;

    @Column(name = "image", length = 255, columnDefinition = "VARCHAR(255) COMMENT '档口图片 URL'")
    private String image;

    @Column(name = "description", columnDefinition = "TEXT COMMENT '档口描述'")
    private String description;

    @Column(name = "status", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态: 1-营业中 2-休息中 3-已关闭'")
    private Integer status = 1;

    @Column(name = "sort", columnDefinition = "INT DEFAULT 0 COMMENT '排序值'")
    private Integer sort;

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
    @JoinColumn(name = "restaurant_id", insertable = false, updatable = false)
    private CampusFacility restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", insertable = false, updatable = false)
    private FacilityFloor floorCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuisine_id", insertable = false, updatable = false)
    private StallCuisine cuisine;
}
