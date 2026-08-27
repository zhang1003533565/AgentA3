package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 确认任务完成请求参数（第五步）
 *
 * 由会议 AI 智能体在识别到"任务负责人本人在会议中明确确认完成"后调用。
 * 服务端会校验：assigneeId 必须等于任务真实负责人，且该负责人必须是当前会议的真实参会人。
 * 不信任 AI 传入的其他身份信息。
 */
@Data
public class AgentConfirmTaskRequest {

    @Schema(description = "声称确认完成的负责人用户 ID（服务端将与任务真实负责人比对）", requiredMode = Schema.RequiredMode.REQUIRED, example = "456")
    private Long assigneeId;

    @Schema(description = "当前会议数字 ID（服务端将校验该负责人是本会议参会人）", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long meetingSessionId;

    @Schema(description = "负责人本人明确表达完成的会议原句", example = "[说话人：李四] 测试数据整理已经完成了。")
    private String evidence;
}
