package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "activity_notice")
@Schema(description = "活动通知实体")
public class ActivityNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "通知ID", example = "1")
    private Long id;

    @Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '关联活动ID'")
    @Schema(description = "关联活动ID", example = "1")
    private Long activityId;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '通知标题'")
    @Schema(description = "通知标题", example = "讲座时间变更通知", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Column(columnDefinition = "TEXT COMMENT '通知内容'")
    @Schema(description = "通知内容", example = "原定于周五的讲座因故推迟到周六...")
    private String content;

    @Column(name = "publisher_id", columnDefinition = "BIGINT COMMENT '发布人ID'")
    @Schema(description = "发布人ID", example = "1001")
    private Long publisherId;

    @Column(name = "publisher_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '发布人名称'")
    @Schema(description = "发布人名称", example = "张老师")
    private String publisherName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'DRAFT' COMMENT '通知状态: DRAFT-草稿, PUBLISHED-已发布'")
    @Schema(description = "通知状态: DRAFT-草稿, PUBLISHED-已发布", example = "PUBLISHED")
    private NoticeStatus status = NoticeStatus.PUBLISHED;

    @Column(name = "publish_time", columnDefinition = "DATETIME COMMENT '发布时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    private Activity activity;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = NoticeStatus.PUBLISHED;
        }
        if (status == NoticeStatus.PUBLISHED && publishTime == null) {
            publishTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public enum NoticeStatus {
        DRAFT("草稿"),
        PUBLISHED("已发布");

        private final String description;

        NoticeStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
