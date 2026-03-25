package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "标记图标信息")
public class MarkerIconInfo {

    @Schema(description = "设施类型")
    private Integer facilityType;

    @Schema(description = "设施类型名称")
    private String facilityTypeName;

    @Schema(description = "图标颜色")
    private String iconColor;

    @Schema(description = "图标URL")
    private String iconUrl;
}
