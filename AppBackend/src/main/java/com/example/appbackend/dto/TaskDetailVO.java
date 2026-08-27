package com.example.appbackend.dto;

import com.example.appbackend.entity.TaskStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务详情 VO（用于返回给前端）
 */
@Data
public class TaskDetailVO {

    /**
     * 任务唯一 ID
     */
    @Schema(description = "任务 ID", example = "1")
    private Long id;

    /**
     * 所属会议 ID
     */
    @Schema(description = "所属会议 ID", example = "100")
    private Long meetingSessionId;

    /**
     * 任务负责人用户 ID
     */
    @Schema(description = "任务负责人用户 ID", example = "5")
    private Long assigneeId;

    /**
     * 任务负责人名称（创建时快照）
     */
    @Schema(description = "任务负责人名称", example = "张三")
    private String assigneeName;

    /**
     * 任务标题
     */
    @Schema(description = "任务标题", example = "进行机器学习模型的算法训练")
    private String title;

    /**
     * 任务详细描述
     */
    @Schema(description = "任务详细描述")
    private String description;

    /**
     * 截止时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    /**
     * 任务状态：PENDING-待完成 / COMPLETED-已完成
     */
    @Schema(description = "任务状态", example = "PENDING")
    private TaskStatus status;

    /**
     * 任务依据（AI 判断来源的会议原文）
     */
    @Schema(description = "任务依据", example = "我负责整理测试数据。")
    private String evidence;

    /**
     * 完成时间（未完成为 null）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    /**
     * 完成确认人 ID（谁确认完成了这个任务）
     */
    @Schema(description = "完成确认人 ID", example = "5")
    private Long completedBy;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
