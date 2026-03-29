package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "merchant_review")
@Schema(description = "商家评价实体")
public class MerchantReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "评价ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "7")
    private Long userId;

    @Column(name = "merchant_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '商家ID'")
    @Schema(description = "商家ID", example = "3")
    private Long merchantId;

    @Column(name = "activity_id", columnDefinition = "BIGINT COMMENT '关联优惠活动ID'")
    @Schema(description = "关联优惠活动ID", example = "5")
    private Long activityId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL COMMENT '评分（1-5）'")
    @Schema(description = "评分（1-5）", example = "5")
    private Integer score;

    @Column(nullable = false, length = 1000, columnDefinition = "VARCHAR(1000) NOT NULL COMMENT '评价内容'")
    @Schema(description = "评价内容", example = "餐厅环境很好，价格实惠...")
    private String content;

    @Column(columnDefinition = "TEXT COMMENT '评价图片列表(JSON数组)'")
    @Schema(description = "评价图片列表(JSON数组)")
    private String images;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 2-已删除'")
    @Schema(description = "状态：1-正常 2-已删除", example = "1")
    private Integer status = 1;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", insertable = false, updatable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    private DiscountActivity activity;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
