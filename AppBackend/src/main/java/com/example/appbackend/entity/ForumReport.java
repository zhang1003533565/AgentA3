package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_report")
@Schema(description = "论坛举报实体")
public class ForumReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "举报ID", example = "1")
    private Long id;

    @Column(name = "reporter_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '举报人ID'")
    private Long reporterId;

    @Column(name = "target_type", nullable = false, columnDefinition = "INT NOT NULL COMMENT '举报目标类型：1-帖子，2-评论'")
    private Integer targetType;

    @Column(name = "target_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '举报目标ID'")
    private Long targetId;

    @Column(name = "reason_type", columnDefinition = "INT COMMENT '举报原因类型'")
    private Integer reasonType;

    @Column(name = "reason_text", length = 100, columnDefinition = "VARCHAR(100) COMMENT '举报原因文本'")
    private String reasonText;

    @Column(columnDefinition = "TEXT COMMENT '举报描述'")
    private String description;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已处理，2-已驳回'")
    private Integer status = 0;

    @Column(name = "handle_action", length = 32, columnDefinition = "VARCHAR(32) COMMENT '处理动作：IGNORE/DELETE_CONTENT'")
    private String handleAction;

    @Column(name = "handle_result", length = 500, columnDefinition = "VARCHAR(500) COMMENT '处理结果'")
    private String handleResult;

    @Column(name = "handle_by", columnDefinition = "BIGINT COMMENT '处理人ID'")
    private Long handleBy;

    @Column(name = "handle_time", columnDefinition = "DATETIME COMMENT '处理时间'")
    private LocalDateTime handleTime;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", insertable = false, updatable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handle_by", insertable = false, updatable = false)
    private User handler;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
