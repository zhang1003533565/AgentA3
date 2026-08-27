package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习目标（学习计划结构化拆解功能）。
 *
 * progress 由已完成任务数量自动计算（0-100），status 取值 pending/in_progress/completed。
 */
@Data
@Entity
@Table(name = "study_goal", indexes = {
        @Index(name = "idx_study_goal_user", columnList = "user_id")
})
public class StudyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 完成百分比 0-100，根据已完成任务数量自动计算。 */
    @Column(nullable = false)
    private Integer progress = 0;

    /** pending / in_progress / completed */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
