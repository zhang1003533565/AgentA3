package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trade_record", indexes = {
        @Index(name = "idx_trade_item", columnList = "item_id"),
        @Index(name = "idx_trade_buyer", columnList = "buyer_id"),
        @Index(name = "idx_trade_seller", columnList = "seller_id"),
        @Index(name = "idx_trade_status", columnList = "status")
})
@Schema(description = "校园市集交易记录实体")
public class TradeRecord {

    public enum TradeStatus {
        WAIT_CONFIRM,
        TRADING,
        COMPLETED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "交易记录ID", example = "1")
    private Long id;

    @Column(name = "item_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '商品ID'")
    @Schema(description = "商品ID", example = "10")
    private Long itemId;

    @Column(name = "buyer_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '买家ID'")
    @Schema(description = "买家ID", example = "3")
    private Long buyerId;

    @Column(name = "seller_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '卖家ID'")
    @Schema(description = "卖家ID", example = "5")
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30) NOT NULL DEFAULT 'WAIT_CONFIRM' COMMENT '交易状态: WAIT_CONFIRM-待确认 TRADING-交易中 COMPLETED-已完成 CANCELLED-已取消'")
    @Schema(description = "交易状态: WAIT_CONFIRM/TRADING/COMPLETED/CANCELLED", example = "WAIT_CONFIRM")
    private TradeStatus status = TradeStatus.WAIT_CONFIRM;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Column(name = "contact_exchange_status", length = 20, columnDefinition = "VARCHAR(20) COMMENT '联系方式交换状态: NONE-未发起 REQUESTED-已请求 PARTIAL-部分同意 EXCHANGED-已交换'")
    @Schema(description = "联系方式交换状态: NONE/REQUESTED/PARTIAL/EXCHANGED")
    private String contactExchangeStatus;

    @Column(name = "contact_exchange_requester_id", columnDefinition = "BIGINT COMMENT '联系方式交换发起人ID'")
    @Schema(description = "联系方式交换发起人ID")
    private Long contactExchangeRequesterId;

    @Column(name = "buyer_contact_agreed", columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '买家是否同意交换联系方式'")
    @Schema(description = "买家是否同意交换联系方式")
    private Boolean buyerContactAgreed = false;

    @Column(name = "seller_contact_agreed", columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '卖家是否同意交换联系方式'")
    @Schema(description = "卖家是否同意交换联系方式")
    private Boolean sellerContactAgreed = false;

    @Column(name = "buyer_contact_content", length = 1000, columnDefinition = "VARCHAR(1000) COMMENT '买家授权联系方式'")
    @Schema(description = "买家授权联系方式")
    private String buyerContactContent;

    @Column(name = "seller_contact_content", length = 1000, columnDefinition = "VARCHAR(1000) COMMENT '卖家授权联系方式'")
    @Schema(description = "卖家授权联系方式")
    private String sellerContactContent;

    @Column(name = "contact_exchange_confirmer_id", columnDefinition = "BIGINT COMMENT '联系方式交换确认人ID'")
    @Schema(description = "联系方式交换确认人ID")
    private Long contactExchangeConfirmerId;

    @Column(name = "requester_contact_content", length = 1000, columnDefinition = "VARCHAR(1000) COMMENT '交换发起人授权联系方式'")
    @Schema(description = "交换发起人授权联系方式")
    private String requesterContactContent;

    @Column(name = "confirmer_contact_content", length = 1000, columnDefinition = "VARCHAR(1000) COMMENT '交换确认人授权联系方式'")
    @Schema(description = "交换确认人授权联系方式")
    private String confirmerContactContent;

    @Column(name = "contact_exchange_time", columnDefinition = "DATETIME COMMENT '联系方式交换完成时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "联系方式交换完成时间")
    private LocalDateTime contactExchangeTime;

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
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
