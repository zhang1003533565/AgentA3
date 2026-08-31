package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建任务请求参数（由 AI 或前端调用）
 */
@Data
public class CreateTaskRequest {

    /**
     * 所属会议 Session ID
     */
    @Schema(description = "所属会议 Session ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long meetingSessionId;

    /**
     * 任务负责人用户 ID
     */
    @Schema(description = "任务负责人用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Long assigneeId;

    /**
     * 任务负责人名称
     */
    @Schema(description = "任务负责人名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String assigneeName;

    /**
     * 任务标题
     */
    @Schema(description = "任务标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "进行机器学习模型的算法训练")
    private String title;

    /**
     * 任务详细描述
     */
    @Schema(description = "任务详细描述", example = "需要在下次汇报前完成模型训练并生成结果")
    private String description;

    /**
     * 截止时间（可以为空，表示未明确）
     */
    @Schema(description = "截止时间（可为空表示未明确）")
    private String deadline;

    /**
     * 任务依据（原始发言记录）
     */
    @Schema(description = "任务依据", requiredMode = Schema.RequiredMode.REQUIRED, example = "我负责整理测试数据。")
    private String evidence;
}
