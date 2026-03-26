package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "导航路线步骤")
public class RouteStep {

    @Schema(description = "导航指令")
    private String instruction;

    @Schema(description = "步骤距离（米）")
    private Double distance;

    @Schema(description = "步骤时长（秒）")
    private Integer duration;

    @Schema(description = "起点")
    private RoutePoint startPoint;

    @Schema(description = "终点")
    private RoutePoint endPoint;
}
