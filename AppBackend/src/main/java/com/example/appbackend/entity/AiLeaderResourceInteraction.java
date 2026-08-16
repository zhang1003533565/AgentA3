package com.example.appbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_leader_resource_interaction", indexes = {
        @Index(name = "idx_ai_resource_interaction_user", columnList = "user_id"),
        @Index(name = "idx_ai_resource_interaction_message", columnList = "message_id"),
        @Index(name = "idx_ai_resource_interaction_created", columnList = "create_time")
})
public class AiLeaderResourceInteraction {

    @Id
    @Column(length = 68, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leader_session_id", nullable = false)
    private Long leaderSessionId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "resource_id", nullable = false, length = 80)
    private String resourceId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
