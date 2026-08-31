package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习任务（隶属 study_goal，由智能体拆解生成）。
 *
 * status 与 is_completed 保持兼容同步；progress_percent 是支持部分完成的真实进度。
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

    /** 任务排程开始日期；历史任务允许为空。 */
    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    /** 任务排程结束日期；历史任务允许为空。 */
    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    /** 高 / 中 / 低 */
    @Column(nullable = false, length = 10)
    private String priority = "中";

    @Column(name = "order_num", nullable = false)
    private Integer orderNum = 0;

    /** pending / in_progress / blocked / skipped / completed */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    /** 前端勾选控制；变更时联动 status 并触发 Goal 进度重算。 */
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    /** 任务完成百分比 0-100，用于按预计天数加权计算目标进度。 */
    @Column(name = "progress_percent", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer progressPercent = 0;

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
