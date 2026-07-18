package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_message")
@Schema(description = "聊天消息实体")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "消息ID", example = "200")
    private Long id;

    @Column(name = "session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '会话ID'")
    @Schema(description = "会话ID", example = "8")
    private Long sessionId;

    @Column(name = "sender_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '发送者ID'")
    @Schema(description = "发送者ID", example = "3")
    private Long senderId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '消息类型: 0-系统交易消息 1-文本 2-图片 3-位置 4-交换联系方式'")
    @Schema(description = "消息类型: 0-系统交易消息 1-文本 2-图片 3-位置 4-交换联系方式", example = "1")
    private Integer messageType = 1;

    @Column(nullable = false, length = 1000, columnDefinition = "VARCHAR(1000) NOT NULL COMMENT '消息内容'")
    @Schema(description = "消息内容", example = "您好，请问还在吗？")
    private String content;

    @Column(name = "is_read", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0 COMMENT '接收方是否已读：0-未读 1-已读（发送方对本消息永远视为已读）'")
    @Schema(description = "接收方是否已读：0-未读 1-已读", example = "false")
    private Boolean isRead = false;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '发送时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发送时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private ChatSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", insertable = false, updatable = false)
    private User sender;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
