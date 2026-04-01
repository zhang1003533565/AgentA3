package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "活动通知请求")
public class ActivityNoticeRequest {

    @NotNull(message = "活动ID不能为空")
    @Schema(description = "关联活动ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activityId;

    @NotBlank(message = "通知标题不能为空")
    @Schema(description = "通知标题", example = "讲座时间变更通知", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "通知内容", example = "原定于周五的讲座因故推迟到周六...")
    private String content;

    @Schema(description = "通知状态: DRAFT-草稿, PUBLISHED-已发布", example = "PUBLISHED")
    private String status = "PUBLISHED";
}
