package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分类统计项（周边分类/设施类型统计）")
public class CountItem {

    @Schema(description = "设施类型")
    private Integer facilityType;

    @Schema(description = "设施类型名称")
    private String facilityTypeName;

    @Schema(description = "数量")
    private Integer count;
}
