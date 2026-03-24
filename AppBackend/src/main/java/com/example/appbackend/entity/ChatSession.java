package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_session")
@Schema(description = "聊天会话实体")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "会话ID", example = "8")
    private Long id;

    @Column(name = "item_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '关联物品ID'")
    @Schema(description = "关联物品ID", example = "10")
    private Long itemId;

    @Column(name = "buyer_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '买家ID'")
    @Schema(description = "买家ID", example = "3")
    private Long buyerId;

    @Column(name = "seller_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '卖家ID'")
    @Schema(description = "卖家ID", example = "5")
    private Long sellerId;

    @Column(name = "last_message", length = 500, columnDefinition = "VARCHAR(500) COMMENT '最后一条消息内容'")
    @Schema(description = "最后一条消息内容", example = "您好，请问还在吗？")
    private String lastMessage;

    @Column(name = "last_time", columnDefinition = "DATETIME COMMENT '最后消息时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后消息时间")
    private LocalDateTime lastTime;

    @Column(name = "buyer_unread_count", columnDefinition = "INT DEFAULT 0 COMMENT '买家未读消息数'")
    @Schema(description = "买家未读消息数", example = "2")
    private Integer buyerUnreadCount = 0;

    @Column(name = "seller_unread_count", columnDefinition = "INT DEFAULT 0 COMMENT '卖家未读消息数'")
    @Schema(description = "卖家未读消息数", example = "0")
    private Integer sellerUnreadCount = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    private SecondhandItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", insertable = false, updatable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    private User seller;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
