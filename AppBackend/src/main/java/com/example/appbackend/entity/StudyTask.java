package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习任务（隶属 study_goal，由智能体拆解生成）。
 *
 * status 与 is_completed 始终同步维护：is_completed=true 时 status=completed，反之为 pending。
 */
@Data
@Entity
@Table(name = "study_task", indexes = {
        @Index(name = "idx_study_task_goal", columnList = "goal_id"),
        @Index(name = "idx_study_task_goal_completed", columnList = "goal_id,is_completed")
})
public class StudyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "task_name", nullable = false, length = 120)
    private String taskName;

    @Column(name = "stage", length = 60)
    private String stage;

    @Column(name = "estimated_days", nullable = false)
    private Integer estimatedDays = 1;

    /** 高 / 中 / 低 */
    @Column(nullable = false, length = 10)
    private String priority = "中";

    @Column(name = "order_num", nullable = false)
    private Integer orderNum = 0;

    /** pending / completed */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    /** 前端勾选控制；变更时联动 status 并触发 Goal 进度重算。 */
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @Column(columnDefinition = "TEXT")
    private String description;

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
