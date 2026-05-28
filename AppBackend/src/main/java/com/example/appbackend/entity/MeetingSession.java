package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_session", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "session_id"}),
        @UniqueConstraint(columnNames = {"room_code"})
})
public class MeetingSession {

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_ENDED = "ended";
    public static final String TYPE_QUICK = "quick";
    public static final String TYPE_RESERVED = "reserved";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '会议会话ID'")
    private String sessionId;

    @Column(name = "room_code", length = 12, columnDefinition = "VARCHAR(12) COMMENT '可分享会议号'")
    private String roomCode;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '创建用户ID'")
    private Long userId;

    @Column(nullable = false, length = 120, columnDefinition = "VARCHAR(120) NOT NULL COMMENT '会议标题'")
    private String title;

    @Column(name = "meeting_type", nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'quick' COMMENT '会议类型：quick/reserved'")
    private String meetingType = TYPE_QUICK;

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'idle' COMMENT '会议状态：idle/active/paused/ended'")
    private String status = STATUS_IDLE;

    @Column(name = "scheduled_start_time", columnDefinition = "DATETIME COMMENT '预约开始时间'")
    private LocalDateTime scheduledStartTime;

    @Column(name = "start_time", columnDefinition = "DATETIME COMMENT '实际开始时间'")
    private LocalDateTime startTime;

    @Column(name = "end_time", columnDefinition = "DATETIME COMMENT '结束时间'")
    private LocalDateTime endTime;

    @Column(name = "last_note", length = 500, columnDefinition = "VARCHAR(500) COMMENT '最后一段会议记录摘要'")
    private String lastNote;

    @Column(name = "record_count", columnDefinition = "INT DEFAULT 0 COMMENT '会议记录数量'")
    private Integer recordCount = 0;

    @Column(name = "result_count", columnDefinition = "INT DEFAULT 0 COMMENT '智能体结果数量'")
    private Integer resultCount = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = STATUS_IDLE;
        }
        if (meetingType == null || meetingType.isBlank()) {
            meetingType = TYPE_QUICK;
        }
        if (recordCount == null) {
            recordCount = 0;
        }
        if (resultCount == null) {
            resultCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
