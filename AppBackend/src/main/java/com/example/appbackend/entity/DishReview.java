package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dish_review")
public class DishReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dish_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '菜品 ID'")
    private Long dishId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户 ID'")
    private Long userId;

    @Column(name = "stall_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '档口 ID'")
    private Long stallId;

    @Column(name = "rating", nullable = false, columnDefinition = "DECIMAL(3,2) NOT NULL COMMENT '评分 (0-5)'")
    private java.math.BigDecimal rating;

    @Column(name = "content", columnDefinition = "TEXT COMMENT '评价内容'")
    private String content;

    @Column(name = "images", columnDefinition = "TEXT COMMENT '评价图片 URLs，逗号分隔'")
    private String images;

    @Column(name = "is_anonymous", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否匿名：1-匿名 0-公开'")
    private Boolean isAnonymous = false;

    @Column(name = "helpful_count", columnDefinition = "INT DEFAULT 0 COMMENT '有帮助数'")
    private Integer helpfulCount;

    @Column(name = "reply_count", columnDefinition = "INT DEFAULT 0 COMMENT '回复数'")
    private Integer replyCount;

    @Column(name = "status", nullable = false, columnDefinition = "INT DEFAULT 1 COMMENT '状态：1-正常 0-隐藏 2-已删除'")
    private Integer status = 1;

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
    @JoinColumn(name = "dish_id", insertable = false, updatable = false)
    private Dish dish;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stall_id", insertable = false, updatable = false)
    private CanteenStall stall;
}