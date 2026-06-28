package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "课表设置更新请求")
public class ScheduleSettingsUpdateRequest {

    @Schema(description = "教务系统学号", example = "32132313")
    @Size(max = 50, message = "教务系统学号长度不能超过50")
    private String jwxStudentId;

    @Schema(description = "教务系统密码", example = "313")
    @Size(max = 100, message = "教务系统密码长度不能超过100")
    private String jwxPassword;

    @Schema(description = "学期开始日期", example = "2026-02-24")
    private String semesterStart;

    @Schema(description = "学年", example = "2025-2026")
    @Size(max = 20, message = "学年长度不能超过20")
    private String academicYear;

    @Schema(description = "学期：1-第一学期，2-第二学期", example = "2")
    private Integer semesterTerm;

    @Schema(description = "是否切换为当前选中学期")
    private Boolean selected;

    @Schema(description = "批量保存的学期设置")
    private List<ScheduleSemesterDTO> semesters;
}
