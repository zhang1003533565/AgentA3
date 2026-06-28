package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "课表设置")
public class ScheduleSettingsDTO {

    @Schema(description = "教务系统学号", example = "32132313")
    private String jwxStudentId;

    @Schema(description = "教务系统密码", example = "313")
    private String jwxPassword;

    @Schema(description = "学期开始日期", example = "2026-02-24")
    private String semesterStart;

    @Schema(description = "当前选中学年", example = "2025-2026")
    private String academicYear;

    @Schema(description = "当前选中学期", example = "2")
    private Integer semesterTerm;

    @Schema(description = "当前选中学期教务代码", example = "12")
    private String semesterCode;

    @Schema(description = "已配置的学期列表")
    private List<ScheduleSemesterDTO> semesters;
}
