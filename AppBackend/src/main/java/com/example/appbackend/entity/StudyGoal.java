package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习目标（学习计划结构化拆解功能）。
 *
 * progress 由任务预计学习天数加权自动计算（0-100），status 取值 pending/in_progress/completed。
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

    /** 排程起始日期；历史数据允许为空。 */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 目标完成日期；历史数据允许为空。 */
    @Column(name = "target_date")
    private LocalDate targetDate;

    /** 完成百分比 0-100，根据任务预计天数加权自动计算。 */
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
