package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "二手物品举报统计")
public class SecondhandReportStatisticsResponse {

    @Schema(description = "举报总数", example = "100")
    private Long total;

    @Schema(description = "待处理数量", example = "20")
    private Long pending;

    @Schema(description = "已处理数量", example = "60")
    private Long handled;

    @Schema(description = "已驳回数量", example = "20")
    private Long rejected;
}
