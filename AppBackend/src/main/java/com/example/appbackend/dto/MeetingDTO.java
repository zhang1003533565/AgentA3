package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MeetingDTO {

    private MeetingDTO() {
    }

    @Data
    @Schema(description = "创建或更新会议请求")
    public static class SessionRequest {
        @Size(max = 120, message = "会议主题最多120字符")
        private String title;

        @Size(max = 20, message = "会议类型最多20字符")
        private String meetingType;

        @Size(max = 20, message = "会议状态最多20字符")
        private String status;

        private LocalDateTime scheduledStartTime;

        private Integer expectedDurationMinutes;

        @Size(max = 20, message = "参会成员最多20人")
        private List<@Size(max = 80, message = "成员名称最多80字符") String> participants = new ArrayList<>();

        @Size(max = 6000, message = "会议记录最多6000字符")
        private String notes;
    }

    @Data
    @Schema(description = "快速发起会议请求")
    public static class QuickMeetingRequest {
        @Size(max = 120, message = "会议主题最多120字符")
        private String title;

        @Size(max = 20, message = "参会成员最多20人")
        private List<@Size(max = 80, message = "成员名称最多80字符") String> participants = new ArrayList<>();
    }

    @Data
    @Schema(description = "预约会议请求")
    public static class ReserveMeetingRequest {
        @Size(max = 120, message = "会议主题最多120字符")
        private String title;

        private LocalDateTime scheduledStartTime;

        private Integer expectedDurationMinutes;

        @Size(max = 20, message = "参会成员最多20人")
        private List<@Size(max = 80, message = "成员名称最多80字符") String> participants = new ArrayList<>();
    }

    @Data
    @Schema(description = "转交主持人请求")
    public static class TransferHostRequest {
        @NotBlank(message = "新主持人姓名不能为空")
        @Size(max = 80, message = "成员名称最多80字符")
        private String newHostName;
    }

    @Data
    @Schema(description = "保存会议记录请求")
    public static class RecordRequest {
        @NotBlank(message = "会议记录不能为空")
        @Size(max = 6000, message = "会议记录最多6000字符")
        private String content;

        @Size(max = 40, message = "记录来源最多40字符")
        private String source;
    }

    @Data
    @Schema(description = "通过会议号加入会议请求")
    public static class JoinRoomRequest {
        @NotBlank(message = "会议号不能为空")
        @Size(max = 12, message = "会议号最多12字符")
        private String roomCode;

        @Size(max = 80, message = "入会名称最多80字符")
        private String displayName;
    }

    @Data
    @Schema(description = "运行会议智能体请求")
    public static class RunAgentRequest {
        @NotBlank(message = "智能体名称不能为空")
        @Size(max = 80, message = "智能体名称最多80字符")
        private String agentName;

        @Size(max = 6000, message = "会议内容最多6000字符")
        private String content;

        @Size(max = 128, message = "模型标识最多128字符")
        private String llmModel;
    }

    @Data
    @Schema(description = "会议列表项")
    public static class SessionItem {
        private String sessionId;
        private Long creatorId;
        private String roomCode;
        private String title;
        private String meetingType;
        private String status;
        private LocalDateTime scheduledStartTime;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String lastNote;
        private Integer participantCount;
        private Integer recordCount;
        private Integer resultCount;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Schema(description = "会议详情")
    public static class SessionDetail {
        private SessionItem session;
        private List<String> participants = new ArrayList<>();
        private List<RecordItem> records = new ArrayList<>();
        private List<AgentResultItem> results = new ArrayList<>();
    }

    @Data
    @Schema(description = "会议记录项")
    public static class RecordItem {
        private Long id;
        private String source;
        private String content;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(description = "会议智能体结果")
    public static class AgentResultItem {
        private Long id;
        private String agentName;
        private String answerType;
        private String answer;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(description = "运行会议智能体响应")
    public static class RunAgentResponse {
        private String sessionId;
        private String agentName;
        private String answerType;
        private String answer;
        private String errorMessage;
        private SessionDetail detail;
    }
}
