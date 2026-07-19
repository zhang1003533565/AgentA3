package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "APP消息中心 DTO")
public class AppMessageDTO {

    @Data
    @Schema(description = "消息列表项")
    public static class MessageVO {
        private Long id;
        private Long userId;
        private String moduleType;
        private String eventType;
        private String title;
        private String content;
        private String targetPage;
        private String targetParams;
        private Long sourceId;
        private String sourceType;
        private Boolean isRead;
        private String createTime;
        private String readTime;
    }

    @Data
    @Schema(description = "未读统计")
    public static class UnreadCountVO {
        private Long total;
        private Long lostFound;
        private Long exam;
    }

    @Data
    @Schema(description = "创建聚合消息命令")
    public static class CreateCommand {
        private Long userId;
        private String moduleType;
        private String eventType;
        private String title;
        private String content;
        private String targetPage;
        private String targetParams;
        private Long sourceId;
        private String sourceType;
    }
}
