package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "标记访问统计响应")
public class MarkerVisitResponse {

    @Schema(description = "标记ID")
    private Long markerId;

    @Schema(description = "标记名称")
    private String markerName;

    @Schema(description = "今日访问次数")
    private Integer todayVisits;

    @Schema(description = "总访问次数")
    private Integer totalVisits;

    @Schema(description = "小时分布")
    private List<TimeSeriesItem> hourlyDistribution;
}
