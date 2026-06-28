package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "课表学期设置")
public class ScheduleSemesterDTO {

    @Schema(description = "学年", example = "2025-2026")
    private String academicYear;

    @Schema(description = "学期：1-第一学期，2-第二学期", example = "2")
    private Integer semesterTerm;

    @Schema(description = "教务系统学期代码", example = "12")
    private String semesterCode;

    @Schema(description = "学期开始日期", example = "2026-03-02")
    private String semesterStart;

    @Schema(description = "是否当前选中学期")
    private Boolean selected;

    @Schema(description = "当前周次")
    private Integer currentWeek;

    @Schema(description = "课程数量")
    private Long courseCount;
}
