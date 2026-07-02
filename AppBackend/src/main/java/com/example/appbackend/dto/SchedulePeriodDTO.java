package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "课表节次时间设置")
public class SchedulePeriodDTO {

    @Schema(description = "第几节课", example = "1")
    private Integer periodIndex;

    @Schema(description = "开始时间", example = "08:00")
    private String startTime;

    @Schema(description = "结束时间", example = "08:45")
    private String endTime;
}
