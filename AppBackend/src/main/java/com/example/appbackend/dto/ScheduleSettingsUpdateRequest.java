package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
}
