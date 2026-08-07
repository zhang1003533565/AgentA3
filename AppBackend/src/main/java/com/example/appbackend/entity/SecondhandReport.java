package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "secondhand_report")
@Schema(description = "二手物品举报实体")
public class SecondhandReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "举报ID", example = "1")
    private Long id;

    @Column(name = "reporter_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '举报人ID'")
    @Schema(description = "举报人ID", example = "5")
    private Long reporterId;

    @Column(name = "reporter_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '举报人姓名'")
    @Schema(description = "举报人姓名", example = "张三")
    private String reporterName;

    @Column(name = "reporter_contact", length = 100, columnDefinition = "VARCHAR(100) COMMENT '举报人联系方式'")
    @Schema(description = "举报人联系方式", example = "13800138000")
    private String reporterContact;

    @Column(name = "item_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '被举报物品ID'")
    @Schema(description = "被举报物品ID", example = "1")
    private Long itemId;

    @Column(name = "item_title", length = 500, columnDefinition = "VARCHAR(500) COMMENT '被举报物品标题'")
    @Schema(description = "被举报物品标题", example = "iPad Air 4 256G")
    private String itemTitle;

    @Column(name = "item_seller_id", columnDefinition = "BIGINT COMMENT '被举报物品卖家ID'")
    @Schema(description = "被举报物品卖家ID", example = "3")
    private Long itemSellerId;

    @Column(name = "item_seller_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '被举报物品卖家姓名'")
    @Schema(description = "被举报物品卖家姓名", example = "李四")
    private String itemSellerName;

    @Column(name = "reason_type", columnDefinition = "INT COMMENT '举报原因类型：1-虚假信息 2-不良行为 3-其他违规'")
    @Schema(description = "举报原因类型：1-虚假信息 2-不良行为 3-其他违规", example = "1")
    private Integer reasonType;

    @Column(columnDefinition = "TEXT COMMENT '举报详细理由'")
    @Schema(description = "举报详细理由", example = "商品描述与实物不符，存在欺诈行为")
    private String reason;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已处理，2-已驳回'")
    @Schema(description = "状态：0-待处理，1-已处理，2-已驳回", example = "0")
    private Integer status = 0;

    @Column(name = "handle_action", length = 32, columnDefinition = "VARCHAR(32) COMMENT '处理动作：IGNORE/OFFLINE_ITEM'")
    @Schema(description = "处理动作", example = "OFFLINE_ITEM")
    private String handleAction;

    @Column(name = "handle_result", length = 500, columnDefinition = "VARCHAR(500) COMMENT '处理结果'")
    @Schema(description = "处理结果", example = "举报成立，已下架商品")
    private String handleResult;

    @Column(name = "handle_by", columnDefinition = "BIGINT COMMENT '处理人ID'")
    @Schema(description = "处理人ID", example = "1")
    private Long handleBy;

    @Column(name = "handle_time", columnDefinition = "DATETIME COMMENT '处理时间'")
    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", insertable = false, updatable = false)
    private User reporter;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
