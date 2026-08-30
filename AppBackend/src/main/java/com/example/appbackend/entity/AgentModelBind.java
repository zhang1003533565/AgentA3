package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent Model Bind Entity
 * 对应数据库表：agent_model_bind
 */
@Data
@Entity
@Table(name = "agent_model_bind")
public class AgentModelBind {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", length = 50, nullable = false)
    private String agentId;

    @Column(name = "model_config_id", nullable = false)
    private Long modelConfigId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (updateTime == null) {
            updateTime = createTime;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
