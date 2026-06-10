package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分段（对标 Dify DocumentSegment）
 */
@Data
@Entity
@Table(name = "kb_document_segment", indexes = {
        @Index(name = "idx_segment_document_id", columnList = "document_id"),
        @Index(name = "idx_segment_dataset_id", columnList = "dataset_id")
})
public class DocumentSegment {

    /** 状态：等待 */
    public static final String STATUS_WAITING = "waiting";
    /** 状态：索引中 */
    public static final String STATUS_INDEXING = "indexing";
    /** 状态：已完成 */
    public static final String STATUS_COMPLETED = "completed";
    /** 状态：错误 */
    public static final String STATUS_ERROR = "error";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属知识库ID'")
    private Long datasetId;

    @Column(name = "document_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属文档ID'")
    private Long documentId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '分段排序位置'")
    private Integer position = 0;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '分段文本内容'")
    private String content;

    @Column(columnDefinition = "TEXT COMMENT 'QA 模式的回答内容'")
    private String answer;

    @Column(name = "word_count", columnDefinition = "INT DEFAULT 0 COMMENT '字数统计'")
    private Integer wordCount = 0;

    @Column(columnDefinition = "INT DEFAULT 0 COMMENT 'Token 数量'")
    private Integer tokens = 0;

    @Column(columnDefinition = "JSON COMMENT '关键词列表（JSON 数组）'")
    private String keywords;

    @Column(name = "index_node_id", length = 128, columnDefinition = "VARCHAR(128) COMMENT '向量库中的节点 ID'")
    private String indexNodeId;

    @Column(name = "hit_count", columnDefinition = "INT DEFAULT 0 COMMENT '命中次数'")
    private Integer hitCount = 0;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用'")
    private Integer enabled = 1;

    @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'completed' COMMENT '分段状态'")
    private String status = STATUS_COMPLETED;

    @Column(name = "error_message", columnDefinition = "TEXT COMMENT '处理错误信息'")
    private String errorMessage;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (position == null) position = 0;
        if (wordCount == null) wordCount = 0;
        if (tokens == null) tokens = 0;
        if (hitCount == null) hitCount = 0;
        if (enabled == null) enabled = 1;
        if (status == null) status = STATUS_COMPLETED;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
