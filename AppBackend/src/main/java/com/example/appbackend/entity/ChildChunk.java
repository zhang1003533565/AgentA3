package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 子片段（对标 Dify ChildChunk，用于层次化父子切片模式）
 */
@Data
@Entity
@Table(name = "kb_child_chunk", indexes = {
        @Index(name = "idx_child_chunk_segment_id", columnList = "segment_id"),
        @Index(name = "idx_child_chunk_document_id", columnList = "document_id")
})
public class ChildChunk {

    /** 类型：自动 */
    public static final String TYPE_AUTOMATIC = "automatic";
    /** 类型：自定义 */
    public static final String TYPE_CUSTOMIZED = "customized";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属知识库ID'")
    private Long datasetId;

    @Column(name = "document_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属文档ID'")
    private Long documentId;

    @Column(name = "segment_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属分段ID'")
    private Long segmentId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '子片段排序位置'")
    private Integer position = 0;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '子片段文本内容'")
    private String content;

    @Column(name = "word_count", columnDefinition = "INT DEFAULT 0 COMMENT '字数统计'")
    private Integer wordCount = 0;

    @Column(name = "index_node_id", length = 128, columnDefinition = "VARCHAR(128) COMMENT '向量库中的节点 ID'")
    private String indexNodeId;

    @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'automatic' COMMENT '类型：automatic / customized'")
    private String type = TYPE_AUTOMATIC;

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
        if (type == null) type = TYPE_AUTOMATIC;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
