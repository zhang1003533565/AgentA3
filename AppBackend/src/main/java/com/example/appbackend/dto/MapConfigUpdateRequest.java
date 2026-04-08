package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "地图配置更新请求")
public class MapConfigUpdateRequest {

    @Schema(description = "地图底图图片地址")
    private String mapImageUrl;

    @Schema(description = "地图中心经度")
    private Double centerLongitude;

    @Schema(description = "地图中心纬度")
    private Double centerLatitude;

    @Schema(description = "缩放级别（1-20）")
    private Integer zoomLevel;

    @Schema(description = "边界范围（JSON格式）")
    private String boundary;

    @Schema(description = "标定模式：boundary/controlPoints")
    private String calibrationMode;

    @Schema(description = "控制点配置（JSON格式）")
    private String controlPoints;
}
