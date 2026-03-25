package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "快速定位响应")
public class LocateResponse {

    @Schema(description = "标记ID")
    private Long markerId;

    @Schema(description = "设施ID")
    private Long facilityId;

    @Schema(description = "标记名称")
    private String markerName;

    @Schema(description = "经度")
    private java.math.BigDecimal longitude;

    @Schema(description = "纬度")
    private java.math.BigDecimal latitude;

    @Schema(description = "缩放级别")
    private Integer zoomLevel;
}
