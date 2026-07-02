package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "课表节次时间批量更新请求")
public class SchedulePeriodUpdateRequest {

    @Valid
    @NotEmpty(message = "节次时间不能为空")
    @Schema(description = "节次时间列表")
    private List<SchedulePeriodDTO> periods;
}
