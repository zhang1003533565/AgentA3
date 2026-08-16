package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_comment", indexes = {
        @Index(name = "idx_meeting_comment_session_time", columnList = "meeting_session_id, create_time")
})
public class MeetingComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '会议主键ID'")
    private Long meetingSessionId;

    @Column(name = "sender_id", columnDefinition = "BIGINT COMMENT '发送者用户ID'")
    private Long senderId;

    @Column(name = "sender_name", length = 80, columnDefinition = "VARCHAR(80) COMMENT '发送者显示名称'")
    private String senderName;

    @Column(nullable = false, length = 1000, columnDefinition = "VARCHAR(1000) NOT NULL COMMENT '评论内容'")
    private String content;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
