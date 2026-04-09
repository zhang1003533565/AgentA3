package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券实体
 */
@Data
@Entity
@Table(name = "user_coupon")
@Schema(description = "用户优惠券实体")
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户 ID'")
    private Long userId;

    @Column(name = "coupon_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '优惠券 ID'")
    private Long couponId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-未使用 2-已使用 3-已过期'")
    private Integer status = 1;

    @Column(name = "claim_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '领取次数'")
    private Integer claimCount = 1;

    @Column(name = "receiver_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '联系人'")
    private String receiverName;

    @Column(name = "receiver_phone", length = 20, columnDefinition = "VARCHAR(20) COMMENT '手机号'")
    private String receiverPhone;

    @Column(columnDefinition = "VARCHAR(255) COMMENT '备注'")
    private String remark;

    @Column(name = "claim_time", columnDefinition = "DATETIME COMMENT '领取时间'")
    private LocalDateTime claimTime;

    @Column(name = "use_time", columnDefinition = "DATETIME COMMENT '使用时间'")
    private LocalDateTime useTime;

    @Column(name = "expiry_time", columnDefinition = "DATETIME COMMENT '过期时间'")
    private LocalDateTime expiryTime;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (claimTime == null) {
            claimTime = now;
        }
        createTime = now;
        updateTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
