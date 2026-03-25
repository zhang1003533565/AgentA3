package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "地图配置响应")
public class MapConfigResponse {

    @Schema(description = "地图中心经度")
    private Double centerLongitude;

    @Schema(description = "地图中心纬度")
    private Double centerLatitude;

    @Schema(description = "缩放级别")
    private Integer zoomLevel;

    @Schema(description = "边界范围")
    private Object boundary;
}
