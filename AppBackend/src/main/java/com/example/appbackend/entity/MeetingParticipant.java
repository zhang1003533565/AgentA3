package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_participant")
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '会议主键ID'")
    private Long meetingSessionId;

    @Column(nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '成员名称'")
    private String name;

    @Column(name = "user_id", columnDefinition = "BIGINT COMMENT '关联用户ID，未注册/未登录时为空'")
    private Long userId;

    @Column(name = "sort_order", columnDefinition = "INT DEFAULT 0 COMMENT '排序'")
    private Integer sortOrder = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_session_id", insertable = false, updatable = false)
    private MeetingSession session;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
