package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 会议个人任务实体
 */
@Data
@Entity
@Table(name = "meeting_task")
public class MeetingTask {

    /**
     * 任务唯一 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "任务 ID", example = "1")
    private Long id;

    /**
     * 所属会议 ID
     */
    @Column(name = "meeting_session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '所属会议 ID'")
    @Schema(description = "所属会议 ID", example = "100")
    private Long meetingSessionId;

    /**
     * 关联用户 ID（任务负责人）
     */
    @Column(name = "assignee_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '任务负责人用户 ID'")
    @Schema(description = "任务负责人用户 ID", example = "5")
    private Long assigneeId;

    /**
     * 任务负责人名称（创建时快照）
     */
    @Column(name = "assignee_name", nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '任务负责人名称快照'")
    @Schema(description = "任务负责人名称", example = "张三")
    private String assigneeName;

    /**
     * 任务标题
     */
    @Column(nullable = false, length = 255, columnDefinition = "VARCHAR(255) NOT NULL COMMENT '任务标题'")
    @Schema(description = "任务标题", example = "进行机器学习模型的算法训练")
    private String title;

    /**
     * 任务详细描述
     */
    @Column(columnDefinition = "TEXT COMMENT '任务详细描述'")
    @Schema(description = "任务详细描述")
    private String description;

    /**
     * 截止时间
     */
    @Column(name = "deadline", columnDefinition = "DATETIME COMMENT '截止时间（未明确为 NULL）'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    /**
     * 任务状态：PENDING-待完成 / COMPLETED-已完成
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待完成 / COMPLETED-已完成'")
    @Schema(description = "任务状态", example = "PENDING")
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * 任务依据（AI 判断来源的会议原文）
     */
    @Column(name = "evidence", nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '任务依据（原始发言记录）'")
    @Schema(description = "任务依据", example = "我负责整理测试数据。")
    private String evidence;

    /**
     * 完成时间（任务完成时记录）
     */
    @Column(name = "completed_at", columnDefinition = "DATETIME COMMENT '完成时间（未完成为 NULL）'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    /**
     * 完成确认人 ID（谁确认完成了这个任务）
     */
    @Column(name = "completed_by", columnDefinition = "BIGINT COMMENT '完成确认人 ID（正常应为 assignee_id）'")
    @Schema(description = "完成确认人 ID", example = "5")
    private Long completedBy;

    /**
     * 创建时间
     */
    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
