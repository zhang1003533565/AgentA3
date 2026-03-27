package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "discount_claim", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_activity", columnNames = {"user_id", "activity_id"})
})
@Schema(description = "优惠活动领取记录实体")
public class DiscountClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "领取记录ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "4")
    private Long userId;

    @Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '活动ID'")
    @Schema(description = "优惠活动ID", example = "1")
    private Long activityId;

    @Column(name = "claim_time", columnDefinition = "DATETIME COMMENT '领取时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "领取时间")
    private LocalDateTime claimTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    private DiscountActivity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        claimTime = LocalDateTime.now();
    }
}
