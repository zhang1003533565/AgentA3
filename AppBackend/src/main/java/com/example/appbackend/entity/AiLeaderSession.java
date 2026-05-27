package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_leader_session", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "session_id"})
})
public class AiLeaderSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '客户端会话ID'")
    private String sessionId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    private Long userId;

    @Column(nullable = false, length = 120, columnDefinition = "VARCHAR(120) NOT NULL COMMENT '会话标题'")
    private String title;

    @Column(name = "last_message", length = 500, columnDefinition = "VARCHAR(500) COMMENT '最后一条消息'")
    private String lastMessage;

    @Column(name = "message_count", columnDefinition = "INT DEFAULT 0 COMMENT '消息数量'")
    private Integer messageCount = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (messageCount == null) {
            messageCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
