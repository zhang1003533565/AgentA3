package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 优惠券领取请求
 */
@Data
@Schema(description = "优惠券领取请求")
public class PromotionCouponClaimRequest {

    @Schema(description = "备注", example = "中午到店使用")
    private String remark;
}
