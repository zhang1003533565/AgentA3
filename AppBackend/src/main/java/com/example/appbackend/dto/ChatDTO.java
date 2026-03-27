package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "二手市场 - 聊天相关请求/响应")
public class ChatDTO {

    @Data
    @Schema(description = "会话列表项响应")
    public static class SessionVO {
        private Long sessionId;
        private Long itemId;
        private String itemTitle;
        private String itemImage;
        private BigDecimal itemPrice;
        private Long sellerId;
        private String sellerName;
        private Long otherUserId;
        private String otherUsername;
        private String otherAvatar;
        private String lastMessage;
        private String lastTime;
        private Integer unreadCount;
        private Boolean isSeller;
    }

    @Data
    @Schema(description = "消息响应")
    public static class MessageVO {
        private Long id;
        private Long sessionId;
        private Long senderId;
        private String senderName;
        private String senderAvatar;
        private String content;
        private Integer messageType;
        private Boolean isRead;
        private Boolean isMine;
        private String createTime;
    }

    @Data
    @Schema(description = "发送消息请求")
    public static class SendMessageRequest {
        @NotNull(message = "会话ID不能为空")
        @Schema(description = "会话ID", example = "8")
        private Long sessionId;

        @NotBlank(message = "消息内容不能为空")
        @Size(min = 1, max = 500, message = "消息内容1-500字")
        @Schema(description = "消息内容", example = "您好，请问还在吗？")
        private String content;

        @Schema(description = "消息类型：1-文本 2-图片 3-位置，默认1")
        private Integer messageType = 1;
    }
}
