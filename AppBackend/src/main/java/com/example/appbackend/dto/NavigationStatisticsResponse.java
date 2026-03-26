package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "导航统计数据响应")
public class NavigationStatisticsResponse {

    @Schema(description = "总导航次数")
    private Integer totalNavigations;

    @Schema(description = "今日导航次数")
    private Integer todayNavigations;

    @Schema(description = "已完成导航次数")
    private Integer completedNavigations;

    @Schema(description = "已取消导航次数")
    private Integer cancelledNavigations;

    @Schema(description = "平均导航时长（秒）")
    private Integer averageDuration;

    @Schema(description = "热门目的地列表")
    private List<MarkerSummaryItem> popularDestinations;

    @Schema(description = "日趋势列表")
    private List<TimeSeriesItem> dailyTrend;
}
