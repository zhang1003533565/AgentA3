package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "时间序列数据项（日趋势/小时分布通用）")
public class TimeSeriesItem {

    @Schema(description = "日期或小时")
    private String label;

    @Schema(description = "小时（小时分布专用）")
    private Integer hour;

    @Schema(description = "数量")
    private Integer count;
}
