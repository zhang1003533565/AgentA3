package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "discount_favorite", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "activity_id"})
})
@Schema(description = "优惠活动收藏实体")
public class DiscountFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "收藏ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "3")
    private Long userId;

    @Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '优惠活动ID'")
    @Schema(description = "优惠活动ID", example = "5")
    private Long activityId;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '收藏时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "收藏时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    private DiscountActivity activity;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
