package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新任务状态请求参数
 */
@Data
public class UpdateTaskStatusRequest {

    /**
     * 任务状态：PENDING-待完成 / COMPLETED-已完成
     */
    @Schema(description = "任务状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "COMPLETED")
    private String status;
}
