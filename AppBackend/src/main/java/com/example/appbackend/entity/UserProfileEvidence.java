package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_profile_evidence", indexes = {
        @Index(name = "idx_user_profile_evidence_user", columnList = "user_id"),
        @Index(name = "idx_user_profile_evidence_dimension", columnList = "dimension_key"),
        @Index(name = "idx_user_profile_evidence_status", columnList = "status"),
        @Index(name = "idx_user_profile_evidence_source", columnList = "source_type"),
        @Index(name = "idx_user_profile_evidence_occurred", columnList = "occurred_at"),
        @Index(name = "idx_user_profile_evidence_create_time", columnList = "create_time")
})
public class UserProfileEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    private Long userId;

    @Column(name = "dimension_key", nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '画像维度标识'")
    private String dimensionKey;

    @Column(name = "source_type", nullable = false, length = 60, columnDefinition = "VARCHAR(60) NOT NULL COMMENT '来源类型：chat/meeting/exam/click/assistant_resource/profile'")
    private String sourceType;

    @Column(name = "source_id", length = 120, columnDefinition = "VARCHAR(120) COMMENT '来源业务ID'")
    private String sourceId;

    @Column(length = 80, columnDefinition = "VARCHAR(80) COMMENT '行为动作，如 asked/completed/clicked/analyzed'")
    private String action;

    @Column(name = "object_type", length = 80, columnDefinition = "VARCHAR(80) COMMENT '证据对象类型，如 conversation/meeting/question/resource'")
    private String objectType;

    @Column(name = "object_id", length = 120, columnDefinition = "VARCHAR(120) COMMENT '证据对象ID'")
    private String objectId;

    @Column(name = "object_name", length = 200, columnDefinition = "VARCHAR(200) COMMENT '证据对象名称'")
    private String objectName;

    @Column(length = 300, columnDefinition = "VARCHAR(300) COMMENT '行为结果摘要'")
    private String result;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '证据内容'")
    private String evidence;

    @Column(length = 40, columnDefinition = "VARCHAR(40) COMMENT '变化方向'")
    private String direction;

    @Column(nullable = false, columnDefinition = "DOUBLE NOT NULL DEFAULT 0.5 COMMENT '证据置信度 0-1'")
    private Double confidence = 0.5;

    @Column(name = "suggested_delta", columnDefinition = "INT DEFAULT 0 COMMENT '建议变化分'")
    private Integer suggestedDelta = 0;

    @Column(name = "applied_delta", columnDefinition = "INT DEFAULT 0 COMMENT '实际应用变化分'")
    private Integer appliedDelta = 0;

    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30) NOT NULL DEFAULT 'candidate' COMMENT '状态：candidate/applied/rejected'")
    private String status = "candidate";

    @Column(length = 300, columnDefinition = "VARCHAR(300) COMMENT '处理原因'")
    private String reason;

    @Lob
    @Column(name = "metadata_json", columnDefinition = "LONGTEXT COMMENT '扩展元数据JSON'")
    private String metadataJson;

    @Lob
    @Column(name = "confidence_breakdown_json", columnDefinition = "LONGTEXT COMMENT '置信度拆解JSON'")
    private String confidenceBreakdownJson;

    @Column(name = "occurred_at", columnDefinition = "DATETIME COMMENT '行为实际发生时间'")
    private LocalDateTime occurredAt;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "apply_time", columnDefinition = "DATETIME COMMENT '应用到画像时间'")
    private LocalDateTime applyTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (confidence == null) {
            confidence = 0.5;
        }
        if (suggestedDelta == null) {
            suggestedDelta = 0;
        }
        if (appliedDelta == null) {
            appliedDelta = 0;
        }
        if (status == null) {
            status = "candidate";
        }
    }
}
