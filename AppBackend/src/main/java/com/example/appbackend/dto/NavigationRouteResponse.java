package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导航路线响应")
public class NavigationRouteResponse {

    @Schema(description = "总距离（米）")
    private Double distance;

    @Schema(description = "总时长（秒）")
    private Integer duration;

    @Schema(description = "出行方式")
    private String mode;

    @Schema(description = "导航步骤列表")
    private List<RouteStep> steps;

    @Schema(description = "路线坐标点")
    private List<RoutePoint> polyline;
}
