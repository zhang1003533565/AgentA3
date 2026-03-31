package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导航响应")
public class NavigationResponse {

    @Schema(description = "导航记录ID")
    private Long navigationId;

    @Schema(description = "起点经度")
    private BigDecimal fromLongitude;

    @Schema(description = "起点纬度")
    private BigDecimal fromLatitude;

    @Schema(description = "目标标记ID")
    private Long toMarkerId;

    @Schema(description = "目标名称")
    private String toMarkerName;

    @Schema(description = "终点经度")
    private BigDecimal toLongitude;

    @Schema(description = "终点纬度")
    private BigDecimal toLatitude;

    @Schema(description = "距离（米）")
    private Double distance;

    @Schema(description = "预计时长（秒）")
    private Integer estimatedTime;

    @Schema(description = "路线坐标点（兼容旧版）")
    private List<RoutePoint> routePoints;

    @Schema(description = "完整路线坐标点（腾讯API真实路线）")
    private List<RoutePoint> polyline;

    @Schema(description = "导航步骤列表")
    private List<RouteStep> steps;
}
