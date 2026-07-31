package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FacilityFloorRequest {

    @NotNull(message = "所属校园设施 ID 不能为空")
    private Long facilityId;

    @NotBlank(message = "楼层名称不能为空")
    private String name;

    private Integer status = 1;

    private Integer sortOrder = 0;
}
