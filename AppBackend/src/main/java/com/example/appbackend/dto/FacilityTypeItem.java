package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设施类型字典项")
public class FacilityTypeItem {

    @Schema(description = "类型编码", example = "1")
    private Integer value;

    @Schema(description = "类型名称", example = "食堂")
    private String label;
}
