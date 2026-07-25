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
        private Integer itemStatus;
        private String itemStatusText;
        private Long sellerId;
        private String sellerName;
        private Long otherUserId;
        private String otherUsername;
        private String otherAvatar;
        private String lastMessage;
        private String lastTime;
        private Integer unreadCount;
        private Boolean isSeller;
        private Long tradeId;
        private String tradeStatus;
        private String tradeStatusText;
        private String contactExchangeStatus;
        private Long contactExchangeRequesterId;
        private ContactExchangeVO contactExchange;
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
        private String tradeAction;
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
        @Size(min = 1, max = 1000, message = "消息内容1-1000字")
        @Schema(description = "消息内容", example = "您好，请问还在吗？")
        private String content;

        @Schema(description = "消息类型：1-文本 2-图片 3-位置 4-交换联系方式，默认1")
        private Integer messageType = 1;

        @Schema(description = "联系方式交换动作：AGREE-同意/发起 DECLINE-暂不交换")
        private String contactExchangeAction;
    }

    @Data
    @Schema(description = "创建交易记录请求")
    public static class CreateTradeRecordRequest {
        @NotNull(message = "商品ID不能为空")
        @Schema(description = "商品ID", example = "10")
        private Long itemId;

        @NotNull(message = "买家ID不能为空")
        @Schema(description = "买家ID", example = "3")
        private Long buyerId;

        @Schema(description = "卖家ID，不传时使用商品发布者", example = "5")
        private Long sellerId;
    }

    @Data
    @Schema(description = "交易记录响应")
    public static class TradeRecordVO {
        private Long id;
        private Long itemId;
        private Long buyerId;
        private Long sellerId;
        private String status;
        private String statusText;
        private String createTime;
        private String updateTime;
        private String itemTitle;
        private BigDecimal itemPrice;
        private Long otherUserId;
        private String otherUsername;
        private String otherAvatar;
        private Boolean isSeller;
        private String contactExchangeStatus;
        private Long contactExchangeRequesterId;
        private ContactExchangeVO contactExchange;
    }

    @Data
    @Schema(description = "联系方式交换状态")
    public static class ContactExchangeVO {
        private String status;
        private Boolean currentUserAgreed;
        private Boolean otherUserAgreed;
        private Boolean canAgree;
        private Boolean canDecline;
        private Long requesterId;
    }

    @Data
    @Schema(description = "交易通知响应")
    public static class TradeNotificationVO {
        private Long id;
        private Long sessionId;
        private Long itemId;
        private String itemTitle;
        private String itemImage;
        private Long tradeId;
        private String tradeStatus;
        private String tradeStatusText;
        private String tradeAction;
        private String content;
        private String createTime;
        private Boolean isRead;
    }
}
