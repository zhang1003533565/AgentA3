package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_leader_message")
public class AiLeaderMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leader_session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT 'Leader会话主键ID'")
    private Long leaderSessionId;

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant'")
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '消息内容'")
    private String content;

    @Column(name = "answer_type", length = 40, columnDefinition = "VARCHAR(40) COMMENT '回答内容类型'")
    private String answerType;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_session_id", insertable = false, updatable = false)
    private AiLeaderSession session;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
