package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库（对标 Dify Dataset）
 */
@Data
@Entity
@Table(name = "kb_dataset")
public class Dataset {

    /** 索引技术：高质量（向量） */
    public static final String INDEXING_HIGH_QUALITY = "high_quality";
    /** 索引技术：经济（关键词） */
    public static final String INDEXING_ECONOMY = "economy";

    /** 权限：仅创建者 */
    public static final String PERMISSION_ONLY_ME = "only_me";
    /** 权限：全部团队 */
    public static final String PERMISSION_ALL_TEAM = "all_team";

    /** 数据源类型：上传文件 */
    public static final String PROVIDER_VENDOR = "vendor";

    /** 文档形态：文本模型 */
    public static final String CHUNK_STRUCTURE_TEXT = "text_model";
    /** 文档形态：QA 模型 */
    public static final String CHUNK_STRUCTURE_QA = "qa_model";
    /** 文档形态：层次模型（父子） */
    public static final String CHUNK_STRUCTURE_HIERARCHICAL = "hierarchical_model";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128, columnDefinition = "VARCHAR(128) NOT NULL COMMENT '知识库名称'")
    private String name;

    @Column(columnDefinition = "TEXT COMMENT '知识库描述'")
    private String description;

    @Column(name = "provider", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'vendor' COMMENT '数据源提供方：vendor'")
    private String provider = PROVIDER_VENDOR;

    @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'only_me' COMMENT '访问权限：only_me / all_team'")
    private String permission = PERMISSION_ONLY_ME;

    @Column(name = "indexing_technique", length = 32, columnDefinition = "VARCHAR(32) COMMENT '索引技术：high_quality / economy'")
    private String indexingTechnique;

    @Column(name = "embedding_model", length = 255, columnDefinition = "VARCHAR(255) COMMENT '向量模型 ID'")
    private String embeddingModel;

    @Column(name = "embedding_model_provider", length = 128, columnDefinition = "VARCHAR(128) COMMENT '向量模型服务商'")
    private String embeddingModelProvider;

    @Column(name = "retrieval_model", columnDefinition = "JSON COMMENT '检索模型配置（JSON）'")
    private String retrievalModel;

    @Column(name = "chunk_structure", length = 32, columnDefinition = "VARCHAR(32) COMMENT '文档切分形态：text_model / qa_model / hierarchical_model'")
    private String chunkStructure;

    @Column(name = "document_count", columnDefinition = "INT DEFAULT 0 COMMENT '文档总数'")
    private Integer documentCount = 0;

    @Column(name = "word_count", columnDefinition = "BIGINT DEFAULT 0 COMMENT '总字数'")
    private Long wordCount = 0L;

    @Column(name = "created_by", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '创建者用户ID'")
    private Long createdBy;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (documentCount == null) documentCount = 0;
        if (wordCount == null) wordCount = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
