package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_profile_dimension", indexes = {
        @Index(name = "idx_user_profile_dimension_user", columnList = "user_id"),
        @Index(name = "idx_user_profile_dimension_key", columnList = "dimension_key")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profile_dimension", columnNames = {"user_id", "dimension_key"})
})
public class UserProfileDimension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    private Long userId;

    @Column(name = "dimension_key", nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '画像维度标识'")
    private String dimensionKey;

    @Column(nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '画像维度名称'")
    private String name;

    @Column(name = "short_name", length = 40, columnDefinition = "VARCHAR(40) COMMENT '雷达图短名称'")
    private String shortName;

    @Column(columnDefinition = "TEXT COMMENT '画像维度说明'")
    private String description;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 70 COMMENT '维度分数 0-100'")
    private Integer score = 70;

    @Column(nullable = false, columnDefinition = "DOUBLE NOT NULL DEFAULT 0.5 COMMENT '画像置信度 0-1'")
    private Double confidence = 0.5;

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'stable' COMMENT '趋势：up/down/stable'")
    private String trend = "stable";

    @Column(name = "evidence_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '累计证据数'")
    private Integer evidenceCount = 0;

    @Lob
    @Column(name = "source_summary_json", columnDefinition = "LONGTEXT COMMENT '证据来源摘要JSON数组'")
    private String sourceSummaryJson;

    @Column(name = "update_policy", length = 40, columnDefinition = "VARCHAR(40) COMMENT '更新节奏策略'")
    private String updatePolicy;

    @Column(name = "last_updated_at", columnDefinition = "DATETIME COMMENT '最近实际更新画像时间'")
    private LocalDateTime lastUpdatedAt;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (score == null) {
            score = 70;
        }
        if (confidence == null) {
            confidence = 0.5;
        }
        if (trend == null) {
            trend = "stable";
        }
        if (evidenceCount == null) {
            evidenceCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
