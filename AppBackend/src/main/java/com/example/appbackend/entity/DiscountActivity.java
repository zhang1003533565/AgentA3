package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "discount_activity")
@Schema(description = "优惠活动实体")
public class DiscountActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "活动ID", example = "1")
    private Long id;

    @Column(name = "merchant_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '商家ID'")
    @Schema(description = "商家ID", example = "3")
    private Long merchantId;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '活动标题'")
    @Schema(description = "活动标题", example = "午餐特价套餐", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Column(columnDefinition = "TEXT COMMENT '活动描述'")
    @Schema(description = "活动描述", example = "周一至周五 11:00-13:00，特价午餐套餐限量供应。")
    private String description;

    @Column(name = "cover_image", length = 255, columnDefinition = "VARCHAR(255) COMMENT '封面图片URL'")
    @Schema(description = "封面图片URL", example = "https://cdn.example.com/activity/cover1.jpg")
    private String coverImage;

    @Column(columnDefinition = "TEXT COMMENT '活动图片列表(JSON数组)'")
    @Schema(description = "活动图片列表(JSON数组)", example = "[\"https://cdn.example.com/activity/img1.jpg\"]")
    private String images;

    @Column(name = "start_time", columnDefinition = "DATETIME COMMENT '活动开始时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "活动开始时间", example = "2024-03-15 00:00:00")
    private LocalDateTime startTime;

    @Column(name = "end_time", columnDefinition = "DATETIME COMMENT '活动结束时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "活动结束时间", example = "2024-04-30 23:59:59")
    private LocalDateTime endTime;

    @Column(name = "use_rules", columnDefinition = "TEXT COMMENT '使用规则'")
    @Schema(description = "使用规则", example = "1. 仅限堂食\\n2. 不可与店内其他优惠叠加")
    private String useRules;

    @Column(name = "total_count", columnDefinition = "INT COMMENT '总名额'")
    @Schema(description = "总名额", example = "100")
    private Integer totalCount;

    @Column(name = "remain_count", columnDefinition = "INT COMMENT '剩余名额'")
    @Schema(description = "剩余名额", example = "50")
    private Integer remainCount;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", insertable = false, updatable = false)
    private Merchant merchant;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
