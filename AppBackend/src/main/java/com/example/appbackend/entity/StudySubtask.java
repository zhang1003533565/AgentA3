package com.example.appbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习任务的可执行细分项。
 *
 * StudyTask 没有子项时仍可作为叶子任务，保证历史计划兼容；有子项时进度由本表叶子项聚合。
 */
@Data
@Entity
@Table(name = "study_subtask", indexes = {
        @Index(name = "idx_study_subtask_task", columnList = "task_id"),
        @Index(name = "idx_study_subtask_task_order", columnList = "task_id,order_num")
})
public class StudySubtask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_name", nullable = false, length = 120)
    private String taskName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_days", nullable = false)
    private Integer estimatedDays = 1;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Column(name = "progress_percent", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer progressPercent = 0;

    /** pending / in_progress / blocked / skipped / completed */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @Column(name = "order_num", nullable = false)
    private Integer orderNum = 0;

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
