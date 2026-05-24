package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "论坛举报统计")
public class ForumReportStatisticsResponse {

    private Long total;
    private Long pending;
    private Long handled;
    private Long rejected;
    private Long postReports;
    private Long commentReports;
}
