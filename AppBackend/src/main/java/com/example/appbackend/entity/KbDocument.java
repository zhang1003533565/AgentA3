package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档（对标 Dify Document）
 */
@Data
@Entity
@Table(name = "kb_document", indexes = {
        @Index(name = "idx_document_dataset_id", columnList = "dataset_id"),
        @Index(name = "idx_document_indexing_status", columnList = "indexing_status")
})
public class KbDocument {

    /** 索引状态：等待中 */
    public static final String STATUS_WAITING = "waiting";
    /** 索引状态：解析中 */
    public static final String STATUS_PARSING = "parsing";
    /** 索引状态：清洗中 */
    public static final String STATUS_CLEANING = "cleaning";
    /** 索引状态：切分中 */
    public static final String STATUS_SPLITTING = "splitting";
    /** 索引状态：索引中 */
    public static final String STATUS_INDEXING = "indexing";
    /** 索引状态：已完成 */
    public static final String STATUS_COMPLETED = "completed";
    /** 索引状态：错误 */
    public static final String STATUS_ERROR = "error";
    /** 索引状态：已暂停 */
    public static final String STATUS_PAUSED = "paused";

    /** 数据来源类型：上传文件 */
    public static final String DATA_SOURCE_UPLOAD = "upload_file";
    /** 数据来源类型：文本输入 */
    public static final String DATA_SOURCE_TEXT = "text_input";

    /** 文档形态：文本模型 */
    public static final String DOC_FORM_TEXT = "text_model";
    /** 文档形态：QA 模型 */
    public static final String DOC_FORM_QA = "qa_model";

    /** 文档类型 */
    public static final String DOC_TYPE_BOOK = "book";
    public static final String DOC_TYPE_WEB_PAGE = "web_page";
    public static final String DOC_TYPE_PAPER = "paper";
    public static final String DOC_TYPE_SOCIAL_MEDIA = "social_media_post";
    public static final String DOC_TYPE_OTHER = "other";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属知识库ID'")
    private Long datasetId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '文档排序位置'")
    private Integer position = 1;

    @Column(name = "data_source_type", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL COMMENT '数据来源类型：upload_file / text_input'")
    private String dataSourceType;

    @Column(name = "data_source_info", columnDefinition = "JSON COMMENT '数据来源信息（JSON：文件名、上传路径等）'")
    private String dataSourceInfo;

    @Column(nullable = false, length = 255, columnDefinition = "VARCHAR(255) NOT NULL COMMENT '文档名称'")
    private String name;

    @Column(name = "indexing_status", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'waiting' COMMENT '索引状态'")
    private String indexingStatus = STATUS_WAITING;

    @Column(name = "doc_form", length = 32, columnDefinition = "VARCHAR(32) COMMENT '文档形态：text_model / qa_model'")
    private String docForm;

    @Column(name = "doc_type", length = 64, columnDefinition = "VARCHAR(64) COMMENT '文档类型'")
    private String docType;

    @Column(name = "doc_metadata", columnDefinition = "JSON COMMENT '文档元数据（JSON：标签、场景等）'")
    private String docMetadata;

    @Column(name = "word_count", columnDefinition = "INT DEFAULT 0 COMMENT '字数统计'")
    private Integer wordCount = 0;

    @Column(columnDefinition = "INT DEFAULT 0 COMMENT 'Token 数量'")
    private Integer tokens = 0;

    @Column(name = "segment_count", columnDefinition = "INT DEFAULT 0 COMMENT '分段数量'")
    private Integer segmentCount = 0;

    @Column(length = 64, columnDefinition = "VARCHAR(64) COMMENT '批次标识'")
    private String batch;

    @Column(name = "process_rule_id", columnDefinition = "BIGINT COMMENT '关联的处理规则ID'")
    private Long processRuleId;

    @Column(name = "indexing_started_at", columnDefinition = "DATETIME COMMENT '索引开始时间'")
    private LocalDateTime indexingStartedAt;

    @Column(name = "parsing_completed_at", columnDefinition = "DATETIME COMMENT '解析完成时间'")
    private LocalDateTime parsingCompletedAt;

    @Column(name = "cleaning_completed_at", columnDefinition = "DATETIME COMMENT '清洗完成时间'")
    private LocalDateTime cleaningCompletedAt;

    @Column(name = "splitting_completed_at", columnDefinition = "DATETIME COMMENT '切分完成时间'")
    private LocalDateTime splittingCompletedAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME COMMENT '索引完成时间'")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT COMMENT '索引错误信息'")
    private String errorMessage;

    @Column(name = "enabled", columnDefinition = "TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用'")
    private Integer enabled = 1;

    @Column(name = "archived", columnDefinition = "TINYINT DEFAULT 0 COMMENT '是否归档：0-否 1-是'")
    private Integer archived = 0;

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
        if (indexingStatus == null) indexingStatus = STATUS_WAITING;
        if (wordCount == null) wordCount = 0;
        if (tokens == null) tokens = 0;
        if (segmentCount == null) segmentCount = 0;
        if (position == null) position = 1;
        if (enabled == null) enabled = 1;
        if (archived == null) archived = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
