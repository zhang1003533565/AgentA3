package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "app_message",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_message_source_user_event", columnNames = {"source_type", "source_id", "user_id", "event_type"})
        },
        indexes = {
                @Index(name = "idx_app_message_user_time", columnList = "user_id, create_time"),
                @Index(name = "idx_app_message_user_read", columnList = "user_id, is_read"),
                @Index(name = "idx_app_message_module", columnList = "module_type")
        }
)
@Schema(description = "APP消息中心聚合消息")
public class AppMessage {

    public static final String MODULE_LOST_FOUND = "LOST_FOUND";
    public static final String MODULE_EXAM = "EXAM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "消息ID")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '接收用户ID'")
    @Schema(description = "接收用户ID")
    private Long userId;

    @Column(name = "module_type", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL COMMENT '模块类型'")
    @Schema(description = "模块类型：LOST_FOUND/FORUM/EXAM/MEETING/LEARNING")
    private String moduleType;

    @Column(name = "event_type", nullable = false, length = 64, columnDefinition = "VARCHAR(64) NOT NULL COMMENT '事件类型'")
    @Schema(description = "事件类型")
    private String eventType;

    @Column(nullable = false, length = 128, columnDefinition = "VARCHAR(128) NOT NULL COMMENT '消息标题'")
    @Schema(description = "消息标题")
    private String title;

    @Column(length = 512, columnDefinition = "VARCHAR(512) COMMENT '消息内容'")
    @Schema(description = "消息内容")
    private String content;

    @Column(name = "target_page", length = 255, columnDefinition = "VARCHAR(255) COMMENT '点击跳转页面'")
    @Schema(description = "点击跳转页面")
    private String targetPage;

    @Column(name = "target_params", length = 1000, columnDefinition = "VARCHAR(1000) COMMENT '跳转参数JSON'")
    @Schema(description = "跳转参数JSON")
    private String targetParams;

    @Column(name = "source_id", columnDefinition = "BIGINT COMMENT '来源记录ID'")
    @Schema(description = "来源记录ID")
    private Long sourceId;

    @Column(name = "source_type", length = 64, columnDefinition = "VARCHAR(64) COMMENT '来源类型'")
    @Schema(description = "来源类型")
    private String sourceType;

    @Column(name = "is_read", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读'")
    @Schema(description = "是否已读")
    private Boolean isRead = false;

    @Column(name = "create_time", nullable = false, columnDefinition = "DATETIME NOT NULL COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "read_time", columnDefinition = "DATETIME COMMENT '阅读时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "阅读时间")
    private LocalDateTime readTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (isRead == null) {
            isRead = false;
        }
    }
}
