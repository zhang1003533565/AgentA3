package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的优惠券 DTO
 */
@Data
@Schema(description = "我的优惠券 DTO")
public class UserCouponDTO {

    @Schema(description = "记录 ID", example = "1")
    private Long id;

    @Schema(description = "用户 ID", example = "2")
    private Long userId;

    @Schema(description = "优惠券 ID", example = "8")
    private Long couponId;

    @Schema(description = "领取次数", example = "2")
    private Integer claimCount;

    @Schema(description = "状态：1-未使用 2-已使用 3-已过期", example = "1")
    private Integer status;

    @Schema(description = "联系人", example = "张三")
    private String receiverName;

    @Schema(description = "手机号", example = "13800138000")
    private String receiverPhone;

    @Schema(description = "备注", example = "晚饭时间使用")
    private String remark;

    @Schema(description = "领取时间")
    private LocalDateTime claimTime;

    @Schema(description = "过期时间")
    private LocalDateTime expiryTime;

    @Schema(description = "优惠券信息")
    private PromotionCouponDTO coupon;
}
