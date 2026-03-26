package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "发起导航请求")
public class NavigationRequest {

    @NotNull(message = "起点经度不能为空")
    @Schema(description = "起点经度")
    private BigDecimal fromLongitude;

    @NotNull(message = "起点纬度不能为空")
    @Schema(description = "起点纬度")
    private BigDecimal fromLatitude;

    @NotNull(message = "目标标记ID不能为空")
    @Schema(description = "目标标记ID")
    private Long toMarkerId;
}
