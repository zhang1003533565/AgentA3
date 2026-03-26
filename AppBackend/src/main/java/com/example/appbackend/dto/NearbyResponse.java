package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "周边设施响应")
public class NearbyResponse {

    @Schema(description = "中心经度")
    private Double centerLongitude;

    @Schema(description = "中心纬度")
    private Double centerLatitude;

    @Schema(description = "搜索半径（米）")
    private Double radius;

    @Schema(description = "总数量")
    private Integer total;

    @Schema(description = "周边设施列表")
    private List<MarkerSummaryItem> list;
}
