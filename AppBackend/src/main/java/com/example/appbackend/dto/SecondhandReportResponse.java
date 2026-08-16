package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "二手物品举报响应")
public class SecondhandReportResponse {

    @Schema(description = "举报ID", example = "1")
    private Long id;

    @Schema(description = "举报人ID", example = "5")
    private Long reporterId;

    @Schema(description = "举报人姓名", example = "张三")
    private String reporterName;

    @Schema(description = "举报人联系方式", example = "13800138000")
    private String reporterContact;

    @Schema(description = "被举报物品ID", example = "1")
    private Long itemId;

    @Schema(description = "被举报物品标题", example = "iPad Air 4 256G")
    private String itemTitle;

    @Schema(description = "被举报物品卖家ID", example = "3")
    private Long itemSellerId;

    @Schema(description = "被举报物品卖家姓名", example = "李四")
    private String itemSellerName;

    @Schema(description = "举报原因类型", example = "1")
    private Integer reasonType;

    @Schema(description = "举报原因类型文本", example = "虚假信息")
    private String reasonTypeText;

    @Schema(description = "举报详细理由", example = "商品描述与实物不符")
    private String reason;

    @Schema(description = "状态：0-待处理，1-已处理，2-已驳回", example = "0")
    private Integer status;

    @Schema(description = "状态文本", example = "待处理")
    private String statusText;

    @Schema(description = "处理动作", example = "OFFLINE_ITEM")
    private String handleAction;

    @Schema(description = "处理结果", example = "举报成立，已下架商品")
    private String handleResult;

    @Schema(description = "处理人ID", example = "1")
    private Long handleBy;

    @Schema(description = "处理人姓名", example = "管理员")
    private String handleByName;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
