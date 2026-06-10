package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库处理规则（对标 Dify DatasetProcessRule）
 */
@Data
@Entity
@Table(name = "kb_dataset_process_rule")
public class DatasetProcessRule {

    /** 自动模式 */
    public static final String MODE_AUTOMATIC = "automatic";
    /** 自定义模式 */
    public static final String MODE_CUSTOM = "custom";
    /** 层次模式（父子切片） */
    public static final String MODE_HIERARCHICAL = "hierarchical";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属知识库ID'")
    private Long datasetId;

    @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL COMMENT '规则模式：automatic / custom / hierarchical'")
    private String mode;

    @Column(columnDefinition = "JSON COMMENT '处理规则详细配置（JSON）'")
    private String rules;

    @Column(name = "created_by", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '创建者用户ID'")
    private Long createdBy;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
